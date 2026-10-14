package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.BadgeFlowRepo;
import com.pk.core.business.repository.BadgeTemplateRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.PromotionRepo;
import com.pk.core.business.service.BadgeService;
import com.pk.core.business.service.PreOrderScope;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminBadgeFlowRequestDto;
import com.pk.core.model.dto.request.AdminBadgeTemplateRequestDto;
import com.pk.core.model.dto.request.BadgeFlowTemplateRefDto;
import com.pk.core.model.dto.response.BadgeFlowCountsDto;
import com.pk.core.model.dto.response.BadgeFlowPageResponseDto;
import com.pk.core.model.dto.response.BadgeFlowResponseDto;
import com.pk.core.model.dto.response.BadgeTemplatePageResponseDto;
import com.pk.core.model.dto.response.BadgeTemplateResponseDto;
import com.pk.core.model.dto.response.SkuBadgeResponseDto;
import com.pk.core.model.entity.BadgeFlowEntity;
import com.pk.core.model.entity.BadgeTemplateEntity;
import com.pk.core.model.entity.CollectionEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.PromotionEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BadgeServiceImpl implements BadgeService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<BadgeFlowTemplateRefDto>> REF_LIST_TYPE = new TypeReference<>() {
    };

    private final BadgeTemplateRepo templates;
    private final BadgeFlowRepo flows;
    private final ProductSkuRepo productSkus;
    private final ProductRepo products;
    private final CollectionRepo collections;
    private final PromotionRepo promotions;
    private final CategoryRepo categories;
    private final PreOrderService preOrder;
    private final ObjectMapper objectMapper;

    // ============================== Storefront ==============================

    /** Dữ liệu cấu hình nạp 1 lần cho cả request. */
    private record Config(Date now, String channel, PreOrderScope preOrderScope,
                          List<BadgeTemplateEntity> activeTemplates, Map<Long, BadgeTemplateEntity> templateById,
                          List<FlowView> flows) {
    }

    private record FlowView(BadgeFlowEntity flow, JsonNode ruleConfig, List<BadgeFlowTemplateRefDto> refs) {
    }

    private record Candidate(BadgeTemplateEntity template, boolean pinned, int flowWeight) {
    }

    @Transactional(readOnly = true)
    @Override
    public List<SkuBadgeResponseDto> forSkus(List<String> skuIds, String channel) {
        List<SkuBadgeResponseDto> result = new ArrayList<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return result;
        }
        Config config = loadConfig(channel);
        Set<Long> seen = new LinkedHashSet<>();
        for (String raw : skuIds) {
            Long skuId = parseLongOrNull(raw);
            if (skuId == null || !seen.add(skuId)) {
                continue;
            }
            ProductSkuEntity sku = productSkus.findOne(skuId);
            if (sku == null) {
                continue;
            }
            List<BadgeTemplateResponseDto> badges = new ArrayList<>();
            if (ProductSkuEntity.PUBLISHED.equals(sku.getStatus())) {
                BadgeTemplateEntity picked = pickFor(config, sku);
                if (picked != null) {
                    badges.add(toDto(picked));
                }
            }
            result.add(new SkuBadgeResponseDto(String.valueOf(skuId), badges));
        }
        return result;
    }

    private Config loadConfig(String channel) {
        Date now = new Date();
        List<BadgeTemplateEntity> active = new ArrayList<>();
        java.util.HashMap<Long, BadgeTemplateEntity> byId = new java.util.HashMap<>();
        for (BadgeTemplateEntity t : templates.findAll(Sort.unsorted())) {
            if (t.getDeletedDate() == null && BadgeTemplateEntity.ACTIVE.equals(t.getStatus())) {
                active.add(t);
                byId.put(t.getId(), t);
            }
        }
        String ch = blank(channel) ? "WEB" : channel.trim().toUpperCase(Locale.ROOT);
        List<FlowView> views = new ArrayList<>();
        for (BadgeFlowEntity f : flows.findAll(Sort.unsorted())) {
            if (!f.isActiveAt(now)) {
                continue;
            }
            if (!BadgeFlowEntity.CHANNEL_ALL.equals(f.getChannel()) && !f.getChannel().equals(ch)) {
                continue;
            }
            views.add(new FlowView(f, objectMapper.valueToTree(normalizeRule(f.getRuleType(), readMap(f.getRuleConfig()))),
                    readRefs(f.getTemplates())));
        }
        PreOrderScope scope = preOrder.scope();
        return new Config(now, ch, scope == null ? PreOrderScope.off() : scope, active, byId, views);
    }

    /**
     * Áp quy tắc ưu tiên PRE_ORDER -> OUT_OF_STOCK -> CAMPAIGN, trả đúng 1 mẫu (hoặc null).
     * PRE_ORDER: SKU nằm trong phạm vi đặt trước đang cấu hình (kể cả còn hàng); cấu hình phạm vi ALL (cũ) thì chỉ SKU hết hàng.
     * OUT_OF_STOCK: tự động theo tồn kho thật (available <= 0). CAMPAIGN: chỉ cho SKU còn hàng.
     */
    private BadgeTemplateEntity pickFor(Config config, ProductSkuEntity sku) {
        boolean outOfStock = sku.available() <= 0;
        PreOrderScope scope = config.preOrderScope();
        boolean preOrderItem;
        if (!scope.enabled()) {
            preOrderItem = false;
        } else if (scope.isAll()) {
            preOrderItem = outOfStock;
        } else {
            ProductEntity product = products.findOne(sku.getProductId());
            Set<Long> collectionIds = new HashSet<>();
            for (CollectionEntity c : collections.findByProductId(sku.getProductId())) {
                collectionIds.add(c.getId());
            }
            preOrderItem = scope.covers(sku.getId(), sku.getProductId(), product == null ? null : product.getCategoryId(),
                    collectionIds);
        }
        if (preOrderItem) {
            BadgeTemplateEntity badge = pickStockBadge(config, BadgeTemplateEntity.BADGE_PRE_ORDER);
            if (badge != null) {
                return badge;
            }
        }
        if (outOfStock) {
            return pickStockBadge(config, BadgeTemplateEntity.BADGE_OUT_OF_STOCK);
        }
        return pickCampaignBadge(config, sku);
    }

    private BadgeTemplateEntity pickStockBadge(Config config, String badgeType) {
        List<Candidate> candidates = new ArrayList<>();
        for (FlowView view : config.flows()) {
            if (!BadgeFlowEntity.RULE_OUT_OF_STOCK.equals(view.flow().getRuleType())) {
                continue;
            }
            collect(candidates, config, view, t -> badgeType.equals(t.getBadgeType()));
        }
        Optional<Candidate> best = candidates.stream().max(CANDIDATE_ORDER);
        if (best.isPresent()) {
            return best.get().template();
        }
        // Không có flow nào cấu hình: dùng mẫu ACTIVE cùng badgeType (trọng số mặc định cao nhất).
        return config.activeTemplates().stream()
                .filter(t -> badgeType.equals(t.getBadgeType()))
                .max(Comparator.comparingInt(BadgeTemplateEntity::getDefaultPriorityWeight)
                        .thenComparing(BadgeTemplateEntity::getId, Comparator.reverseOrder()))
                .orElse(null);
    }

    private BadgeTemplateEntity pickCampaignBadge(Config config, ProductSkuEntity sku) {
        ProductEntity product = products.findOne(sku.getProductId());
        Set<Long> collectionIds = new HashSet<>();
        for (CollectionEntity c : collections.findByProductId(sku.getProductId())) {
            collectionIds.add(c.getId());
        }
        List<PromotionEntity> activePromotions = null;

        List<Candidate> candidates = new ArrayList<>();
        for (FlowView view : config.flows()) {
            String rule = view.flow().getRuleType();
            if (BadgeFlowEntity.RULE_OUT_OF_STOCK.equals(rule)) {
                continue;
            }
            boolean match;
            switch (rule) {
                case BadgeFlowEntity.RULE_ALL -> match = true;
                case BadgeFlowEntity.RULE_MANUAL -> match = idsContain(view.ruleConfig(), "skuIds", sku.getId())
                        || idsContain(view.ruleConfig(), "productIds", sku.getProductId());
                case BadgeFlowEntity.RULE_CATEGORY -> match = product != null
                        && idsContain(view.ruleConfig(), "categoryIds", product.getCategoryId());
                case BadgeFlowEntity.RULE_COLLECTION -> match = idsIntersect(view.ruleConfig(), "collectionIds",
                        collectionIds);
                case BadgeFlowEntity.RULE_PROMOTION -> {
                    if (activePromotions == null) {
                        activePromotions = promotions.findActiveByProductId(sku.getProductId(), config.now());
                    }
                    Set<Long> promotionIds = new HashSet<>();
                    for (PromotionEntity p : activePromotions) {
                        promotionIds.add(p.getId());
                    }
                    match = !promotionIds.isEmpty()
                            && (!hasIds(view.ruleConfig(), "promotionIds")
                            || idsIntersect(view.ruleConfig(), "promotionIds", promotionIds));
                }
                default -> match = false;
            }
            if (match) {
                collect(candidates, config, view, t -> !BadgeTemplateEntity.BADGE_PRE_ORDER.equals(t.getBadgeType())
                        && !BadgeTemplateEntity.BADGE_OUT_OF_STOCK.equals(t.getBadgeType()));
            }
        }
        return candidates.stream().max(CANDIDATE_ORDER).map(Candidate::template).orElse(null);
    }

    /** Ghim trước; rồi trọng số trong flow; rồi trọng số mặc định của mẫu; cuối cùng id nhỏ hơn thắng. */
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparing(Candidate::pinned)
            .thenComparingInt(Candidate::flowWeight)
            .thenComparingInt(c -> c.template().getDefaultPriorityWeight())
            .thenComparing(c -> c.template().getId(), Comparator.reverseOrder());

    private static void collect(List<Candidate> out, Config config, FlowView view,
                                java.util.function.Predicate<BadgeTemplateEntity> filter) {
        for (BadgeFlowTemplateRefDto ref : view.refs()) {
            Long templateId = parseLongOrNull(ref.getBadgeTemplateId());
            BadgeTemplateEntity template = templateId == null ? null : config.templateById().get(templateId);
            if (template == null || !filter.test(template)) {
                continue;
            }
            out.add(new Candidate(template, Boolean.TRUE.equals(ref.getIsPinned()),
                    ref.getPriorityWeight() == null ? 0 : ref.getPriorityWeight()));
        }
    }

    private static boolean hasIds(JsonNode rule, String field) {
        return rule != null && rule.has(field) && rule.get(field).isArray() && rule.get(field).size() > 0;
    }

    private static boolean idsContain(JsonNode rule, String field, Long id) {
        if (id == null || !hasIds(rule, field)) {
            return false;
        }
        for (JsonNode n : rule.get(field)) {
            if (id.equals(parseLongOrNull(n.asText()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean idsIntersect(JsonNode rule, String field, Set<Long> ids) {
        if (!hasIds(rule, field)) {
            return false;
        }
        for (JsonNode n : rule.get(field)) {
            Long id = parseLongOrNull(n.asText());
            if (id != null && ids.contains(id)) {
                return true;
            }
        }
        return false;
    }

    // ============================== Admin: templates ==============================

    @Transactional(readOnly = true)
    @Override
    public BadgeTemplatePageResponseDto findTemplates(int page, int take, String status, String type, String badgeType,
                                                      String defaultPosition, String search) {
        List<BadgeTemplateEntity> matched = new ArrayList<>();
        String needle = blank(search) ? null : search.trim().toLowerCase(Locale.ROOT);
        for (BadgeTemplateEntity t : templates.findAll(Sort.unsorted())) {
            if (t.getDeletedDate() != null) {
                continue;
            }
            if (needle != null && !contains(t.getName(), needle) && !contains(t.getCode(), needle)) {
                continue;
            }
            if (!matches(status, t.getStatus()) || !matches(type, t.getType()) || !matches(badgeType, t.getBadgeType())
                    || !matches(defaultPosition, t.getDefaultPosition())) {
                continue;
            }
            matched.add(t);
        }
        matched.sort(Comparator.comparing(BadgeTemplateEntity::getId).reversed());
        PageResponse<BadgeTemplateEntity> paged = PageResponse.paginate(matched, page, take);
        return new BadgeTemplatePageResponseDto(paged.content().stream().map(this::toDto).toList(),
                paged.totalElements(), paged.page(), paged.size());
    }

    @Transactional(readOnly = true)
    @Override
    public BadgeTemplateResponseDto findTemplateById(String badgeId) {
        return toDto(requireTemplate(badgeId));
    }

    @Transactional
    @Override
    public BadgeTemplateResponseDto createTemplate(AdminBadgeTemplateRequestDto req) {
        String code = req.getCode().trim();
        if (templates.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Mã mẫu nhãn đã tồn tại: " + code, code);
        }
        BadgeTemplateEntity t = new BadgeTemplateEntity();
        t.setCode(code);
        t.setName(req.getName().trim());
        t.setDescription(req.getDescription());
        t.setType(requireIn(req.getType(), BadgeTemplateEntity.TYPES, "type"));
        t.setBadgeType(requireIn(req.getBadgeType(), BadgeTemplateEntity.BADGE_TYPES, "badgeType"));
        t.setStatus(blank(req.getStatus()) ? BadgeTemplateEntity.DRAFT
                : requireIn(req.getStatus(), BadgeTemplateEntity.STATUSES, "status"));
        t.setDisplayText(req.getDisplayText());
        t.setDefaultPosition(blank(req.getDefaultPosition()) ? "TOP_LEFT"
                : requireIn(req.getDefaultPosition(), BadgeTemplateEntity.POSITIONS, "defaultPosition"));
        t.setStyleConfig(toJson(req.getStyleConfig()));
        t.setIcon(req.getIcon());
        t.setImage(req.getImage());
        t.setIconMobile(req.getIconMobile());
        t.setImageMobile(req.getImageMobile());
        t.setAssetMeta(toJson(req.getAssetMeta()));
        t.setDefaultPriorityWeight(req.getDefaultPriorityWeight() == null ? 0 : req.getDefaultPriorityWeight());
        templates.create(t);
        return toDto(t);
    }

    /** PATCH: chỉ ghi field khác null; code và status KHÔNG đổi qua endpoint này (spec). */
    @Transactional
    @Override
    public BadgeTemplateResponseDto updateTemplate(String badgeId, AdminBadgeTemplateRequestDto req) {
        BadgeTemplateEntity t = requireTemplate(badgeId);
        if (req.getName() != null) {
            if (req.getName().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "name không được để trống");
            }
            t.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            t.setDescription(req.getDescription());
        }
        if (req.getType() != null) {
            String newType = requireIn(req.getType(), BadgeTemplateEntity.TYPES, "type");
            if (!newType.equals(t.getType())) {
                clearAssetsNotUsedBy(t, newType);
            }
            t.setType(newType);
        }
        if (req.getBadgeType() != null) {
            t.setBadgeType(requireIn(req.getBadgeType(), BadgeTemplateEntity.BADGE_TYPES, "badgeType"));
        }
        if (req.getDisplayText() != null) {
            t.setDisplayText(req.getDisplayText());
        }
        if (req.getDefaultPosition() != null) {
            t.setDefaultPosition(requireIn(req.getDefaultPosition(), BadgeTemplateEntity.POSITIONS, "defaultPosition"));
        }
        if (req.getStyleConfig() != null) {
            t.setStyleConfig(toJson(req.getStyleConfig()));
        }
        // Quy tắc xoá: field = null -> giữ nguyên; chuỗi rỗng "" -> xoá (đặt null); assetMeta = {} -> xoá.
        if (req.getImage() != null) {
            t.setImage(blank(req.getImage()) ? null : req.getImage());
        }
        if (req.getStatus() != null) {
            // Cho phép chuyển DRAFT -> ACTIVE (và ngược lại) qua PATCH.
            t.setStatus(requireIn(req.getStatus(), BadgeTemplateEntity.STATUSES, "status"));
        }
        if (req.getIcon() != null) {
            t.setIcon(blank(req.getIcon()) ? null : req.getIcon());
        }
        if (req.getIconMobile() != null) {
            t.setIconMobile(blank(req.getIconMobile()) ? null : req.getIconMobile());
        }
        if (req.getImageMobile() != null) {
            t.setImageMobile(blank(req.getImageMobile()) ? null : req.getImageMobile());
        }
        if (req.getAssetMeta() != null) {
            t.setAssetMeta(req.getAssetMeta().isEmpty() ? null : toJson(req.getAssetMeta()));
        }
        if (req.getDefaultPriorityWeight() != null) {
            t.setDefaultPriorityWeight(req.getDefaultPriorityWeight());
        }
        t.touch();
        templates.update(t);
        return toDto(t);
    }

    @Transactional
    @Override
    public void deleteTemplate(String badgeId) {
        BadgeTemplateEntity t = requireTemplate(badgeId);
        if (BadgeTemplateEntity.ACTIVE.equals(t.getStatus())) {
            throw BusinessException.conflict(ErrorCode.BADGE_IN_USE,
                    "Mẫu nhãn đang ACTIVE, hãy chuyển sang INACTIVE/DRAFT trước khi xoá", badgeId);
        }
        List<String> usedBy = new ArrayList<>();
        for (BadgeFlowEntity f : flows.findAll(Sort.unsorted())) {
            for (BadgeFlowTemplateRefDto ref : readRefs(f.getTemplates())) {
                if (t.getId().equals(parseLongOrNull(ref.getBadgeTemplateId()))) {
                    usedBy.add(f.getName());
                    break;
                }
            }
        }
        if (!usedBy.isEmpty()) {
            throw BusinessException.conflict(ErrorCode.BADGE_IN_USE,
                    "Mẫu nhãn đang được dùng trong luồng: " + String.join(", ", usedBy) + ". Gỡ khỏi luồng trước khi xoá",
                    badgeId);
        }
        t.setCode(t.getCode() + "~" + t.getId());
        t.setDeletedDate(new Date());
        t.touch();
        templates.update(t);
    }

    // ============================== Admin: flows ==============================

    @Transactional(readOnly = true)
    @Override
    public BadgeFlowPageResponseDto findFlows(int page, int take, String status, String search, String displayStatus) {
        if (!blank(status) && !BadgeFlowEntity.STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "status không hợp lệ: " + status, status);
        }
        if (!blank(displayStatus) && !DISPLAY_STATUSES.contains(displayStatus.trim().toUpperCase(Locale.ROOT))) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "displayStatus không hợp lệ: " + displayStatus + " (" + String.join(" | ", DISPLAY_STATUSES) + ")", displayStatus);
        }
        Date now = new Date();
        String needle = blank(search) ? null : search.trim().toLowerCase(Locale.ROOT);
        List<BadgeFlowEntity> all = new ArrayList<>();
        for (BadgeFlowEntity f : flows.findAll(Sort.unsorted())) {
            if (needle == null || contains(f.getName(), needle) || contains(f.getDescription(), needle)) {
                all.add(f);
            }
        }
        long active = all.stream().filter(f -> BadgeFlowEntity.ACTIVE.equals(f.getStatus())).count();
        long inactive = all.stream().filter(f -> BadgeFlowEntity.INACTIVE.equals(f.getStatus())).count();
        long draft = all.stream().filter(f -> BadgeFlowEntity.DRAFT.equals(f.getStatus())).count();
        BadgeFlowCountsDto counts = new BadgeFlowCountsDto(all.size(), active, inactive, draft);
        java.util.Map<String, Long> byDisplay = new java.util.LinkedHashMap<>();
        for (String ds : DISPLAY_STATUSES) {
            byDisplay.put(ds, all.stream().filter(f -> ds.equals(displayStatus(f, now))).count());
        }
        counts.setDisplayStatus(byDisplay);

        List<BadgeFlowEntity> matched = new ArrayList<>(all.stream()
                .filter(f -> matches(status, f.getStatus()) && matches(displayStatus, displayStatus(f, now))).toList());
        matched.sort(Comparator.comparing(BadgeFlowEntity::getId).reversed());
        PageResponse<BadgeFlowEntity> paged = PageResponse.paginate(matched, page, take);
        return new BadgeFlowPageResponseDto(paged.content().stream().map(f -> toDto(f, now)).toList(),
                paged.totalElements(), paged.page(), paged.size(), counts);
    }

    @Transactional
    @Override
    public BadgeFlowResponseDto createFlow(AdminBadgeFlowRequestDto req) {
        BadgeFlowEntity f = new BadgeFlowEntity();
        applyFlow(f, req);
        flows.create(f);
        return toDto(f, new Date());
    }

    @Transactional
    @Override
    public BadgeFlowResponseDto updateFlow(String flowId, AdminBadgeFlowRequestDto req) {
        Long id = parseLongOrNull(flowId);
        BadgeFlowEntity f = id == null ? null : flows.findOne(id);
        if (f == null) {
            throw new ResourceNotFoundException("Luồng nhãn", "BadgeFlow", flowId);
        }
        if (ENDED.equals(displayStatus(f, new Date()))) {
            throw BusinessException.conflict(ErrorCode.BADGE_FLOW_LOCKED,
                    "Chiến dịch đã kết thúc, không thể sửa. Hãy tạo chiến dịch mới", flowId);
        }
        applyFlow(f, req);
        f.touch();
        flows.update(f);
        return toDto(f, new Date());
    }

    @Transactional
    @Override
    public void deleteFlow(String flowId) {
        Long id = parseLongOrNull(flowId);
        BadgeFlowEntity f = id == null ? null : flows.findOne(id);
        if (f == null) {
            throw new ResourceNotFoundException("Luồng nhãn", "BadgeFlow", flowId);
        }
        String ds = displayStatus(f, new Date());
        if (SCHEDULED.equals(ds) || RUNNING.equals(ds)) {
            throw BusinessException.conflict(ErrorCode.BADGE_FLOW_LOCKED,
                    "Chiến dịch đang " + ds + ", hãy tạm dừng (INACTIVE) trước khi xoá", flowId);
        }
        flows.delete(f);
    }

    private void applyFlow(BadgeFlowEntity f, AdminBadgeFlowRequestDto req) {
        if (req.getActiveFrom() != null && req.getActiveTo() != null && req.getActiveTo().before(req.getActiveFrom())) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "activeTo phải sau activeFrom");
        }
        f.setName(req.getName().trim());
        f.setDescription(req.getDescription());
        f.setActiveFrom(req.getActiveFrom());
        f.setActiveTo(req.getActiveTo());
        f.setStatus(blank(req.getStatus()) ? BadgeFlowEntity.DRAFT
                : requireIn(req.getStatus(), BadgeFlowEntity.STATUSES, "status"));
        f.setRuleType(requireIn(req.getRuleType(), BadgeFlowEntity.RULE_TYPES, "ruleType"));
        Map<String, Object> rule = normalizeRule(f.getRuleType(), req.getRuleConfig());
        f.setRuleConfig(req.getRuleConfig() == null && rule.isEmpty() ? null : toJson(rule));
        f.setChannel(blank(req.getChannel()) ? BadgeFlowEntity.CHANNEL_ALL
                : requireIn(req.getChannel(), BadgeFlowEntity.CHANNELS, "channel"));

        List<BadgeFlowTemplateRefDto> normalized = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (BadgeFlowTemplateRefDto ref : req.getTemplates()) {
            Long templateId = parseLongOrNull(ref.getBadgeTemplateId());
            BadgeTemplateEntity template = templateId == null ? null : templates.findOne(templateId);
            if (template == null || template.getDeletedDate() != null) {
                throw BusinessException.badRequest(ErrorCode.NOT_FOUND,
                        "Không tìm thấy mẫu nhãn: " + ref.getBadgeTemplateId(), ref.getBadgeTemplateId());
            }
            if (!used.add(templateId)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Mẫu nhãn bị lặp trong templates: " + templateId, templateId);
            }
            normalized.add(new BadgeFlowTemplateRefDto(String.valueOf(templateId),
                    ref.getPriorityWeight() == null ? 0 : ref.getPriorityWeight(),
                    Boolean.TRUE.equals(ref.getIsPinned())));
        }
        f.setTemplates(toJson(normalized));
        if (BadgeFlowEntity.ACTIVE.equals(f.getStatus())) {
            validateScope(f.getRuleType(), rule);
        }
    }

    // ============================== helpers ==============================

    // ============================== scope: chuẩn hoá + kiểm tra ==============================

    private static final String ENDED = "ENDED";
    private static final String SCHEDULED = "SCHEDULED";
    private static final String RUNNING = "RUNNING";

    /** DRAFT | PAUSED | SCHEDULED | RUNNING | ENDED (khớp BadgeCampaignDisplayStatus của FE). */
    private static final List<String> DISPLAY_STATUSES = List.of("DRAFT", "PAUSED", "SCHEDULED", "RUNNING", "ENDED");

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    static String displayStatus(BadgeFlowEntity f, Date now) {
        if (BadgeFlowEntity.DRAFT.equals(f.getStatus())) {
            return "DRAFT";
        }
        if (BadgeFlowEntity.INACTIVE.equals(f.getStatus())) {
            return "PAUSED";
        }
        if (f.getActiveTo() != null && f.getActiveTo().before(now)) {
            return ENDED;
        }
        if (f.getActiveFrom() != null && f.getActiveFrom().after(now)) {
            return SCHEDULED;
        }
        return RUNNING;
    }

    /**
     * Chuẩn hoá ruleConfig về id chuẩn (categoryIds / collectionIds / promotionIds / skuIds + productIds) từ cả dạng chuẩn
     * lẫn payload picker của FE (categories[{categoryId}], collections[{collectionId}], promotions[{id}],
     * targets[{productId, productVariantId}]). GIỮ NGUYÊN field gốc để FE mở lại picker. MANUAL: target có
     * productVariantId chỉ vào skuIds (không nới sang cả sản phẩm); chỉ có productId mới vào productIds.
     */
    static Map<String, Object> normalizeRule(String ruleType, Map<String, Object> raw) {
        Map<String, Object> m = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        if (ruleType == null) {
            return m;
        }
        switch (ruleType) {
            case BadgeFlowEntity.RULE_CATEGORY ->
                    putIds(m, "categoryIds", union(idList(m.get("categoryIds")), idsOf(m.get("categories"), "categoryId", "id")));
            case BadgeFlowEntity.RULE_COLLECTION ->
                    putIds(m, "collectionIds", union(idList(m.get("collectionIds")), idsOf(m.get("collections"), "collectionId", "id")));
            case BadgeFlowEntity.RULE_PROMOTION ->
                    putIds(m, "promotionIds", union(idList(m.get("promotionIds")), idsOf(m.get("promotions"), "id", "promotionId")));
            case BadgeFlowEntity.RULE_MANUAL -> {
                Set<Long> skuIds = new LinkedHashSet<>(idList(m.get("skuIds")));
                Set<Long> productIds = new LinkedHashSet<>(idList(m.get("productIds")));
                if (m.get("targets") instanceof Collection<?> targets) {
                    for (Object o : targets) {
                        if (!(o instanceof Map<?, ?> t)) {
                            continue;
                        }
                        Long variant = firstLong(t, "productVariantId", "skuId", "variantId");
                        if (variant != null) {
                            skuIds.add(variant);
                        } else {
                            Long product = firstLong(t, "productId");
                            if (product != null) {
                                productIds.add(product);
                            }
                        }
                    }
                }
                putIds(m, "skuIds", skuIds);
                putIds(m, "productIds", productIds);
            }
            default -> {
            }
        }
        return m;
    }

    private static void putIds(Map<String, Object> m, String key, Collection<Long> ids) {
        if (!ids.isEmpty()) {
            m.put(key, new ArrayList<>(ids));
        }
    }

    private static Set<Long> union(Collection<Long> a, Collection<Long> b) {
        Set<Long> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static Set<Long> idList(Object value) {
        Set<Long> out = new LinkedHashSet<>();
        if (value instanceof Collection<?> c) {
            for (Object o : c) {
                Long id = toLong(o);
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static Set<Long> idsOf(Object value, String... keys) {
        Set<Long> out = new LinkedHashSet<>();
        if (value instanceof Collection<?> c) {
            for (Object o : c) {
                Long id = o instanceof Map<?, ?> map ? firstLong(map, keys) : toLong(o);
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static Long firstLong(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Long id = toLong(map.get(k));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static Long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return o instanceof String str ? parseLongOrNull(str) : null;
    }

    /** Chỉ chạy khi flow ACTIVE: scope bắt buộc phải có và mọi id phải tồn tại (không lưu scope rỗng rồi báo ACTIVE). */
    private void validateScope(String ruleType, Map<String, Object> rule) {
        switch (ruleType) {
            case BadgeFlowEntity.RULE_CATEGORY -> {
                Set<Long> ids = requireScope(rule, "categoryIds", "danh mục");
                for (Long id : ids) {
                    var c = categories.findOne(id);
                    if (c == null || c.getDeletedDate() != null) {
                        throw scopeInvalid("Không tìm thấy danh mục id=" + id, id);
                    }
                }
            }
            case BadgeFlowEntity.RULE_COLLECTION -> {
                Set<Long> ids = requireScope(rule, "collectionIds", "bộ sưu tập");
                for (Long id : ids) {
                    CollectionEntity c = collections.findOne(id);
                    if (c == null || c.getDeletedDate() != null) {
                        throw scopeInvalid("Không tìm thấy bộ sưu tập id=" + id, id);
                    }
                }
            }
            case BadgeFlowEntity.RULE_PROMOTION -> {
                Set<Long> ids = requireScope(rule, "promotionIds", "khuyến mãi");
                for (Long id : ids) {
                    PromotionEntity p = promotions.findOne(id);
                    if (p == null || p.getDeletedDate() != null) {
                        throw scopeInvalid("Không tìm thấy khuyến mãi id=" + id, id);
                    }
                }
            }
            case BadgeFlowEntity.RULE_MANUAL -> {
                Set<Long> skuIds = idList(rule.get("skuIds"));
                Set<Long> productIds = idList(rule.get("productIds"));
                if (skuIds.isEmpty() && productIds.isEmpty()) {
                    throw scopeInvalid("Chọn ít nhất 1 SKU hoặc sản phẩm cho luồng MANUAL", ruleType);
                }
                for (Long id : skuIds) {
                    if (productSkus.findOne(id) == null) {
                        throw scopeInvalid("Không tìm thấy SKU id=" + id, id);
                    }
                }
                for (Long id : productIds) {
                    ProductEntity p = products.findOne(id);
                    if (p == null || p.getDeletedDate() != null) {
                        throw scopeInvalid("Không tìm thấy sản phẩm id=" + id, id);
                    }
                }
            }
            default -> {
            }
        }
    }

    private Set<Long> requireScope(Map<String, Object> rule, String key, String label) {
        Set<Long> ids = idList(rule.get(key));
        if (ids.isEmpty()) {
            throw scopeInvalid("Chọn ít nhất 1 " + label + " trước khi kích hoạt luồng", key);
        }
        return ids;
    }

    private static BusinessException scopeInvalid(String message, Object arg) {
        return BusinessException.badRequest(ErrorCode.BADGE_SCOPE_INVALID, message, arg);
    }

    /** Đổi loại mẫu: bỏ asset không thuộc loại mới (TEXT: không asset; ICON: chỉ icon; IMAGE/MINI_BANNER: chỉ image). */
    private static void clearAssetsNotUsedBy(BadgeTemplateEntity t, String newType) {
        boolean keepIcon = "ICON".equals(newType);
        boolean keepImage = "IMAGE".equals(newType) || "MINI_BANNER".equals(newType);
        if (!keepIcon) {
            t.setIcon(null);
            t.setIconMobile(null);
        }
        if (!keepImage) {
            t.setImage(null);
            t.setImageMobile(null);
        }
        if (!keepIcon && !keepImage) {
            t.setAssetMeta(null);
        }
    }


    private BadgeTemplateEntity requireTemplate(String badgeId) {
        Long id = parseLongOrNull(badgeId);
        BadgeTemplateEntity t = id == null ? null : templates.findOne(id);
        if (t == null || t.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Mẫu nhãn", "BadgeTemplate", badgeId);
        }
        return t;
    }

    private BadgeTemplateResponseDto toDto(BadgeTemplateEntity t) {
        return new BadgeTemplateResponseDto(String.valueOf(t.getId()), t.getName(), t.getCode(), t.getDescription(),
                t.getType(), t.getBadgeType(), t.getStatus(), t.getDisplayText(), t.getDefaultPosition(),
                readMap(t.getStyleConfig()), t.getIcon(), t.getImage(), t.getDefaultPriorityWeight(), t.getIconMobile(),
                t.getImageMobile(), readMap(t.getAssetMeta()));
    }

    private BadgeFlowResponseDto toDto(BadgeFlowEntity f, Date now) {
        return new BadgeFlowResponseDto(String.valueOf(f.getId()), f.getName(), f.getDescription(), f.getStatus(),
                f.isActiveAt(now), f.getActiveFrom(), f.getActiveTo(), f.getRuleType(), readMap(f.getRuleConfig()),
                f.getChannel(), readRefs(f.getTemplates()), displayStatus(f, now));
    }

    private static String requireIn(String value, Set<String> allowed, String field) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    field + " không hợp lệ: " + value + " (cho phép: " + new java.util.TreeSet<>(allowed) + ")", value);
        }
        return v;
    }

    private static boolean matches(String filter, String actual) {
        return blank(filter) || filter.trim().equalsIgnoreCase(actual);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Long parseLongOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "JSON không hợp lệ");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (blank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private JsonNode readTree(String json) {
        if (blank(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<BadgeFlowTemplateRefDto> readRefs(String json) {
        if (blank(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, REF_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
    }
}

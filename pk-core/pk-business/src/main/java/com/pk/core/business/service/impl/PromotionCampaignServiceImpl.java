package com.pk.core.business.service.impl;

import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.PromoDraftComponentRepo;
import com.pk.core.business.repository.PromoDraftRepo;
import com.pk.core.business.repository.PromoPublishedRepo;
import com.pk.core.business.repository.PromoSchemaComponentRepo;
import com.pk.core.business.repository.PromoSchemaRepo;
import com.pk.core.business.repository.PromoVoucherCodeRepo;
import com.pk.core.business.repository.PromotionRepo;
import com.pk.core.business.repository.VoucherRepo;
import com.pk.core.business.service.PromotionCampaignService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.PromoDraftComponentEntity;
import com.pk.core.model.entity.PromoDraftEntity;
import com.pk.core.model.entity.PromoPublishedEntity;
import com.pk.core.model.entity.PromoSchemaComponentEntity;
import com.pk.core.model.entity.PromoSchemaEntity;
import com.pk.core.model.entity.PromoVoucherCodeEntity;
import com.pk.core.model.entity.PromotionEntity;
import com.pk.core.model.entity.VoucherEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PromotionCampaignServiceImpl implements PromotionCampaignService {

    static final String TENANT = "default";
    private static final Set<String> DRAFT_STATUSES = Set.of("EDITING", "READY_TO_PUBLISH", "PUBLISHED", "DISCARDED");
    private static final Set<String> PUBLISHED_STATUSES = Set.of("SCHEDULED", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED");
    private static final Set<String> SCHEMA_TYPES = Set.of("MARKDOWN", "FLAT_PRICE", "VOUCHER");
    private static final Set<String> COST_SOURCES = Set.of("RETAIL", "ECOMMERCE", "CRM_CS");
    private static final Set<String> VOUCHER_STATUSES = Set.of("ACTIVE", "INACTIVE", "DISABLED");

    private final PromoDraftRepo drafts;
    private final PromoDraftComponentRepo draftComponents;
    private final PromoSchemaRepo schemas;
    private final PromoSchemaComponentRepo schemaComponents;
    private final PromoVoucherCodeRepo voucherCodes;
    private final PromoPublishedRepo published;
    private final PromotionRepo promotions;
    private final VoucherRepo vouchers;
    private final ProductRepo products;
    private final ProductSkuRepo skus;
    private final CategoryRepo categories;
    private final CollectionRepo collections;

    // ================================================================== NHÁP

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> listDrafts(Integer page, Integer limit, String status, String search) {
        int p = page == null || page < 1 ? 1 : page;
        int l = limit == null || limit < 1 ? 20 : Math.min(limit, 200);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!st.isEmpty() && !DRAFT_STATUSES.contains(st)) {
            throw PromoJson.invalid("status không hợp lệ", "status");
        }
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Object[]> rows = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : List.of("EDITING", "READY_TO_PUBLISH", "PUBLISHED", "DISCARDED")) {
            counts.put(s, 0);
        }
        List<PromoDraftEntity> all = drafts.findAll(Sort.unsorted()).stream()
                .filter(d -> q.isEmpty() || (d.getName() != null && d.getName().toLowerCase(Locale.ROOT).contains(q)))
                .sorted(Comparator.comparing(PromoDraftEntity::getId).reversed()).toList();
        List<PromoDraftEntity> matched = new ArrayList<>();
        for (PromoDraftEntity d : all) {
            String eff = effectiveStatus(d);
            counts.merge(eff, 1, Integer::sum);
            if (st.isEmpty() ? !"DISCARDED".equals(eff) : st.equals(eff)) {
                matched.add(d);
            }
        }
        int from = Math.min((p - 1) * l, matched.size());
        List<Map<String, Object>> items = matched.subList(from, Math.min(from + l, matched.size())).stream().map(this::draftDto).toList();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", p);
        meta.put("limit", l);
        meta.put("total", matched.size());
        meta.put("totalPages", (int) Math.ceil(matched.size() / (double) l));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("meta", meta);
        out.put("statusCounts", counts);
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> getDraft(String draftId) {
        return draftDto(draft(draftId));
    }

    @Transactional
    @Override
    public Map<String, Object> createDraft(Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = new PromoDraftEntity();
        applyDraft(d, body, true);
        d.setCreatedId(actorId);
        drafts.create(d);
        return draftDto(d);
    }

    @Transactional
    @Override
    public Map<String, Object> updateDraft(String draftId, Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        applyDraft(d, body, false);
        d.setUpdatedId(actorId);
        d.touch();
        drafts.update(d);
        return draftDto(d);
    }

    @Transactional
    @Override
    public void deleteDraft(String draftId, Long actorId) {
        PromoDraftEntity d = draft(draftId);
        if ("PUBLISHED".equals(d.getStatus())) {
            throw BusinessException.conflict("PROMOTION_DRAFT_PUBLISHED", "Chiến dịch đã phát hành, hãy kết thúc thay vì xoá");
        }
        d.setStatus("DISCARDED");
        d.setUpdatedId(actorId);
        d.touch();
        drafts.update(d);
    }

    private void applyDraft(PromoDraftEntity d, Map<String, Object> b, boolean creating) {
        if (creating || b.containsKey("name")) {
            String name = PromoJson.str(b.get("name"));
            if (name == null || name.isEmpty() || name.length() > 150) {
                throw PromoJson.invalid("Tên chiến dịch bắt buộc, tối đa 150 ký tự", "name");
            }
            d.setName(name);
        }
        if (b.containsKey("description")) {
            String desc = PromoJson.str(b.get("description"));
            d.setDescription(desc == null || desc.isEmpty() ? null : desc);
        }
        if (creating || b.containsKey("startsAt")) {
            d.setStartsAt(required(PromoJson.date(b.get("startsAt"), "startsAt"), "startsAt"));
        }
        if (creating || b.containsKey("endsAt")) {
            d.setEndsAt(required(PromoJson.date(b.get("endsAt"), "endsAt"), "endsAt"));
        }
        if (!d.getEndsAt().after(d.getStartsAt())) {
            throw PromoJson.invalid("Thời gian kết thúc phải sau thời gian bắt đầu", "endsAt");
        }
    }

    private static Date required(Date v, String field) {
        if (v == null) {
            throw PromoJson.invalid("Bắt buộc nhập " + field, field);
        }
        return v;
    }

    // ================================================================== THÀNH PHẦN CẤP CHIẾN DỊCH

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> draftComponents(String draftId) {
        PromoDraftEntity d = draft(draftId);
        return componentsDto(d.getId());
    }

    private Map<String, Object> componentsDto(Long draftId) {
        List<PromoDraftComponentEntity> comps = componentsOf(draftId);
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (PromoDraftComponentEntity c : comps) {
            Map<String, Object> dto = draftComponentDto(c);
            items.add(dto);
            out.put(c.getKind().toLowerCase(Locale.ROOT), dto);
        }
        out.putIfAbsent("target", null);
        out.putIfAbsent("audience", null);
        out.putIfAbsent("applicability", null);
        out.put("applicabilityDefault", defaultApplicability());
        out.put("items", items);
        return out;
    }

    @Transactional
    @Override
    public Map<String, Object> upsertDraftComponent(String draftId, String kind, Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        Map<String, Object> raw = PromoJson.asMap(body.get("config"));
        if (raw == null) {
            throw PromoJson.invalid("Thiếu config", "config");
        }
        Map<String, Object> cfg = switch (kind) {
            case "TARGET" -> validateTarget(raw);
            case "AUDIENCE" -> validateAudience(raw);
            case "APPLICABILITY" -> validateApplicability(raw);
            default -> throw PromoJson.invalid("Loại thành phần không hợp lệ", "kind");
        };
        PromoDraftComponentEntity c = componentsOf(d.getId()).stream().filter(x -> kind.equals(x.getKind())).findFirst().orElse(null);
        if (c == null) {
            c = new PromoDraftComponentEntity();
            c.setDraftId(d.getId());
            c.setKind(kind);
            c.setSortOrder(switch (kind) {
                case "TARGET" -> 0;
                case "AUDIENCE" -> 1;
                default -> 2;
            });
            c.setConfig(PromoJson.write(cfg));
            c.setCreatedId(actorId);
            draftComponents.create(c);
        } else {
            c.setConfig(PromoJson.write(cfg));
            c.setUpdatedId(actorId);
            c.touch();
            draftComponents.update(c);
        }
        return draftComponentDto(c);
    }

    private Map<String, Object> validateTarget(Map<String, Object> raw) {
        String type = PromoJson.str(raw.get("type"));
        Map<String, Object> lineFilter = PromoJson.asMap(raw.get("lineFilter"));
        if (lineFilter != null && lineFilter.get("discountState") != null && !"ANY".equals(PromoJson.str(lineFilter.get("discountState")))) {
            throw PromoJson.invalid("Chưa hỗ trợ lọc theo trạng thái khuyến mãi sẵn có (chỉ ANY)", "lineFilter.discountState");
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", type);
        cfg.put("version", PromotionAuthoringCatalog.VERSION);
        if ("ALL".equals(type)) {
            // không có tham số
        } else if ("CATEGORY".equals(type)) {
            List<String> ids = nonEmpty(PromoJson.strings(raw.get("categoryIds")), "Chọn ít nhất 1 danh mục", "categoryIds");
            for (String id : ids) {
                CategoryEntity c = PromoJson.id(id) == null ? null : categories.findOne(PromoJson.id(id));
                if (c == null || c.getDeletedDate() != null) {
                    throw PromoJson.invalid("Danh mục không tồn tại: " + id, "categoryIds");
                }
            }
            cfg.put("categoryIds", ids);
        } else if ("COLLECTION".equals(type)) {
            List<String> ids = nonEmpty(PromoJson.strings(raw.get("collectionIds")), "Chọn ít nhất 1 bộ sưu tập", "collectionIds");
            for (String id : ids) {
                var c = PromoJson.id(id) == null ? null : collections.findOne(PromoJson.id(id));
                if (c == null || c.getDeletedDate() != null) {
                    throw PromoJson.invalid("Bộ sưu tập không tồn tại: " + id, "collectionIds");
                }
            }
            cfg.put("collectionIds", ids);
        } else if ("MANUAL".equals(type)) {
            List<String> ids = nonEmpty(PromoJson.strings(raw.get("skuIds")), "Chọn ít nhất 1 SKU", "skuIds");
            cfg.put("skuIds", new ArrayList<>(new LinkedHashSet<>(requireSkus(ids, "skuIds"))));
        } else if ("UPLOAD_FILE".equals(type)) {
            throw PromoJson.invalid("Chưa hỗ trợ nhập danh sách sản phẩm từ file; hãy chọn thủ công", "type");
        } else {
            throw PromoJson.invalid("Loại đối tượng sản phẩm không hợp lệ", "type");
        }
        List<String> excluded = PromoJson.strings(raw.get("excludedSkuIds"));
        if (!excluded.isEmpty()) {
            cfg.put("excludedSkuIds", new ArrayList<>(new LinkedHashSet<>(requireSkus(excluded, "excludedSkuIds"))));
        }
        cfg.put("lineFilter", Map.of("discountState", "ANY"));
        return cfg;
    }

    private Map<String, Object> validateAudience(Map<String, Object> raw) {
        if (!"ALL".equals(PromoJson.str(raw.get("type")))) {
            throw PromoJson.invalid("Hiện chỉ hỗ trợ áp dụng cho tất cả khách hàng", "type");
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", "ALL");
        cfg.put("version", PromotionAuthoringCatalog.VERSION);
        return cfg;
    }

    private Map<String, Object> validateApplicability(Map<String, Object> raw) {
        List<String> channels = PromoJson.strings(raw.get("channels"));
        if (channels.isEmpty()) {
            throw PromoJson.invalid("Chọn ít nhất 1 kênh bán", "channels");
        }
        for (String ch : channels) {
            if (!"ONLINE".equals(ch)) {
                throw PromoJson.invalid("Hiện chỉ hỗ trợ kênh ONLINE", "channels");
            }
        }
        Map<String, Object> stores = PromoJson.asMap(raw.get("stores"));
        if (stores != null && stores.get("type") != null && !"ALL".equals(PromoJson.str(stores.get("type")))) {
            throw PromoJson.invalid("Phạm vi cửa hàng chỉ áp dụng cho kênh POS (chưa hỗ trợ)", "stores");
        }
        return defaultApplicability();
    }

    private static Map<String, Object> defaultApplicability() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", "CHANNEL_STORE_APPLICABILITY");
        cfg.put("version", PromotionAuthoringCatalog.VERSION);
        cfg.put("channels", List.of("ONLINE"));
        cfg.put("stores", Map.of("type", "ALL", "version", PromotionAuthoringCatalog.VERSION));
        return cfg;
    }

    private List<String> requireSkus(List<String> ids, String field) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            ProductSkuEntity s = PromoJson.id(id) == null ? null : skus.findOne(PromoJson.id(id));
            if (s == null || s.getDeletedDate() != null) {
                throw PromoJson.invalid("SKU không tồn tại: " + id, field);
            }
            out.add(String.valueOf(s.getId()));
        }
        return out;
    }

    private static List<String> nonEmpty(List<String> l, String message, String field) {
        if (l.isEmpty()) {
            throw PromoJson.invalid(message, field);
        }
        return l;
    }

    private Map<String, Object> draftComponentDto(PromoDraftComponentEntity c) {
        Map<String, Object> cfg = PromoJson.readMap(c.getConfig());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", String.valueOf(c.getId()));
        dto.put("tenantCode", TENANT);
        dto.put("promotionDraftId", String.valueOf(c.getDraftId()));
        dto.put("componentKind", c.getKind());
        dto.put("sortOrder", c.getSortOrder());
        dto.put("config", cfg);
        if ("TARGET".equals(c.getKind())) {
            List<String> selected = PromoJson.strings(cfg.get("skuIds"));
            if (!selected.isEmpty()) {
                dto.put("skus", skuItems(selected));
            }
            List<String> excluded = PromoJson.strings(cfg.get("excludedSkuIds"));
            if (!excluded.isEmpty()) {
                dto.put("excludedSkus", skuItems(excluded));
            }
        }
        return dto;
    }

    List<Map<String, Object>> skuItems(List<String> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : ids) {
            ProductSkuEntity s = PromoJson.id(id) == null ? null : skus.findOne(PromoJson.id(id));
            if (s == null) {
                continue;
            }
            ProductEntity p = s.getProductId() == null ? null : products.findOne(s.getProductId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(s.getId()));
            m.put("skuCode", s.getSkuCode());
            m.put("name", s.getName());
            m.put("productId", s.getProductId() == null ? null : String.valueOf(s.getProductId()));
            m.put("productName", p == null ? null : p.getName());
            m.put("imageUrl", p == null ? null : p.getThumbnailUrl());
            m.put("status", s.getStatus());
            out.add(m);
        }
        return out;
    }

    // ================================================================== SCHEMA (hình thức khuyến mãi)

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> listSchemas(String draftId) {
        PromoDraftEntity d = draft(draftId);
        PromoPublishedEntity pub = publishedOf(d);
        return Map.of("items", schemasOf(d.getId()).stream().map(s -> schemaDto(s, d, pub)).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> getSchema(String draftId, String schemaId) {
        PromoDraftEntity d = draft(draftId);
        return schemaDto(schema(d, schemaId), d, publishedOf(d));
    }

    @Transactional
    @Override
    public Map<String, Object> createSchema(String draftId, Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        PromoSchemaEntity s = new PromoSchemaEntity();
        s.setDraftId(d.getId());
        String type = PromoJson.str(body.get("schemaType"));
        if (type == null || !SCHEMA_TYPES.contains(type)) {
            throw PromoJson.invalid("schemaType chỉ nhận MARKDOWN, FLAT_PRICE hoặc VOUCHER", "schemaType");
        }
        s.setSchemaType(type);
        String scope = PromoJson.str(body.get("scope"));
        String expectedScope = "VOUCHER".equals(type) ? "CART" : "SKU_ITEM";
        if (scope != null && !scope.isEmpty() && !expectedScope.equals(scope)) {
            throw PromoJson.invalid("Phạm vi áp dụng của " + type + " phải là " + expectedScope, "scope");
        }
        s.setScope(expectedScope);
        applySchemaHeader(s, d, body, true);
        int next = schemasOf(d.getId()).stream().mapToInt(PromoSchemaEntity::getSortOrder).max().orElse(-1) + 1;
        s.setSortOrder(next);
        s.setPriority(next);
        s.setCreatedId(actorId);
        schemas.create(s);
        if ("IMPLICIT_ALWAYS".equals(s.getConditionMode())) {
            saveAlwaysCondition(s, actorId);
        }
        return schemaDto(s, d, null);
    }

    @Transactional
    @Override
    public Map<String, Object> updateSchema(String draftId, String schemaId, Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        PromoSchemaEntity s = schema(d, schemaId);
        if (body.containsKey("schemaType") && !s.getSchemaType().equals(PromoJson.str(body.get("schemaType")))) {
            throw PromoJson.invalid("Không đổi được loại hình thức khuyến mãi", "schemaType");
        }
        String before = s.getConditionMode();
        applySchemaHeader(s, d, body, false);
        s.setUpdatedId(actorId);
        s.touch();
        schemas.update(s);
        if ("IMPLICIT_ALWAYS".equals(s.getConditionMode()) && !"IMPLICIT_ALWAYS".equals(before)) {
            saveAlwaysCondition(s, actorId);
        }
        return schemaDto(s, d, null);
    }

    @Transactional
    @Override
    public void deleteSchema(String draftId, String schemaId) {
        PromoDraftEntity d = editableDraft(draftId);
        PromoSchemaEntity s = schema(d, schemaId);
        PromoVoucherCodeEntity v = voucherOf(s.getId());
        if (v != null) {
            voucherCodes.delete(v);
        }
        for (PromoSchemaComponentEntity c : schemaCompsOf(s.getId())) {
            schemaComponents.delete(c);
        }
        schemas.delete(s);
    }

    @Transactional
    @Override
    public void reorderSchemas(String draftId, List<String> schemaIds, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        List<PromoSchemaEntity> current = schemasOf(d.getId());
        Set<String> want = new LinkedHashSet<>(schemaIds == null ? List.of() : schemaIds);
        Set<String> have = new HashSet<>();
        current.forEach(s -> have.add(String.valueOf(s.getId())));
        if (!want.equals(have) || want.size() != (schemaIds == null ? 0 : schemaIds.size())) {
            throw PromoJson.invalid("schemaIds phải gồm đúng và đủ các hình thức khuyến mãi của chiến dịch", "schemaIds");
        }
        int i = 0;
        for (String id : want) {
            PromoSchemaEntity s = current.stream().filter(x -> String.valueOf(x.getId()).equals(id)).findFirst().orElseThrow();
            s.setSortOrder(i);
            s.setPriority(i);
            s.setUpdatedId(actorId);
            s.touch();
            schemas.update(s);
            i++;
        }
    }

    private void applySchemaHeader(PromoSchemaEntity s, PromoDraftEntity d, Map<String, Object> b, boolean creating) {
        if (creating || b.containsKey("name")) {
            String name = PromoJson.str(b.get("name"));
            if (name == null || name.isEmpty() || name.length() > 150) {
                throw PromoJson.invalid("Tên hình thức khuyến mãi bắt buộc, tối đa 150 ký tự", "name");
            }
            s.setName(name);
        }
        if (b.containsKey("description")) {
            String desc = PromoJson.str(b.get("description"));
            s.setDescription(desc == null || desc.isEmpty() ? null : desc);
        }
        if (creating || b.containsKey("costSources")) {
            List<String> cs = PromoJson.strings(b.get("costSources"));
            if (cs.isEmpty()) {
                cs = List.of("ECOMMERCE");
            }
            for (String c : cs) {
                if (!COST_SOURCES.contains(c)) {
                    throw PromoJson.invalid("Nguồn chi phí không hợp lệ: " + c, "costSources");
                }
            }
            s.setCostSources(String.join(",", new LinkedHashSet<>(cs)));
        }
        if (creating || b.containsKey("status")) {
            String st = PromoJson.str(b.get("status"));
            st = st == null || st.isEmpty() ? "ENABLED" : st;
            if (!"ENABLED".equals(st) && !"DISABLED".equals(st)) {
                throw PromoJson.invalid("status chỉ nhận ENABLED hoặc DISABLED", "status");
            }
            s.setStatus(st);
        }
        if (creating || b.containsKey("conditionMode")) {
            String cm = PromoJson.str(b.get("conditionMode"));
            cm = cm == null || cm.isEmpty() ? "EXPLICIT" : cm;
            if (!"EXPLICIT".equals(cm) && !"IMPLICIT_ALWAYS".equals(cm)) {
                throw PromoJson.invalid("conditionMode chỉ nhận EXPLICIT hoặc IMPLICIT_ALWAYS", "conditionMode");
            }
            s.setConditionMode(cm);
        }
        if (b.containsKey("schemaStartsAt")) {
            s.setSchemaStartsAt(PromoJson.date(b.get("schemaStartsAt"), "schemaStartsAt"));
        }
        if (b.containsKey("schemaEndsAt")) {
            s.setSchemaEndsAt(PromoJson.date(b.get("schemaEndsAt"), "schemaEndsAt"));
        }
        Date from = s.getSchemaStartsAt() == null ? d.getStartsAt() : s.getSchemaStartsAt();
        Date to = s.getSchemaEndsAt() == null ? d.getEndsAt() : s.getSchemaEndsAt();
        if (from.before(d.getStartsAt()) || to.after(d.getEndsAt())) {
            throw PromoJson.invalid("Thời gian của hình thức khuyến mãi phải nằm trong thời gian chiến dịch", "schemaStartsAt");
        }
        if (!to.after(from)) {
            throw PromoJson.invalid("Thời gian kết thúc phải sau thời gian bắt đầu", "schemaEndsAt");
        }
    }

    private void saveAlwaysCondition(PromoSchemaEntity s, Long actorId) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", "ALWAYS");
        cfg.put("version", PromotionAuthoringCatalog.VERSION);
        saveSchemaComponent(s, "CONDITION", cfg, new LinkedHashMap<>(), actorId);
    }

    // ---------------------------------------------------------------- điều kiện / quyền lợi của schema

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> schemaComponents(String draftId, String schemaId) {
        PromoDraftEntity d = draft(draftId);
        return schemaComponentsDto(schema(d, schemaId));
    }

    private Map<String, Object> schemaComponentsDto(PromoSchemaEntity s) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        out.put("condition", null);
        out.put("benefit", null);
        for (PromoSchemaComponentEntity c : schemaCompsOf(s.getId())) {
            Map<String, Object> dto = schemaComponentDto(c);
            items.add(dto);
            out.put(c.getKind().toLowerCase(Locale.ROOT), dto);
        }
        out.put("items", items);
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> componentOptions(String draftId, String schemaId, String kind) {
        PromoSchemaEntity s = schema(draft(draftId), schemaId);
        return "CONDITION".equals(kind) ? PromotionAuthoringCatalog.conditionOptions(s.getSchemaType())
                : PromotionAuthoringCatalog.benefitOptions(s.getSchemaType());
    }

    @Transactional
    @Override
    public Map<String, Object> upsertSchemaComponent(String draftId, String schemaId, String kind, Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        PromoSchemaEntity s = schema(d, schemaId);
        String configType = PromoJson.str(body.get("configType"));
        String version = PromoJson.str(body.get("configVersion"));
        Map<String, Object> values = PromoJson.asMap(body.get("values"));
        if (configType == null || configType.isEmpty()) {
            throw PromoJson.invalid("Thiếu configType", "configType");
        }
        if (values == null) {
            values = new LinkedHashMap<>();
        }
        Map<String, Object> cfg = "CONDITION".equals(kind)
                ? PromotionAuthoringCatalog.conditionConfig(s.getSchemaType(), configType, version, values)
                : PromotionAuthoringCatalog.benefitConfig(s.getSchemaType(), configType, version, values);
        PromoSchemaComponentEntity c = saveSchemaComponent(s, kind, cfg, PromotionAuthoringCatalog.authoringValues(cfg), actorId);
        return schemaComponentDto(c);
    }

    private PromoSchemaComponentEntity saveSchemaComponent(PromoSchemaEntity s, String kind, Map<String, Object> cfg, Map<String, Object> authoring, Long actorId) {
        PromoSchemaComponentEntity c = schemaCompsOf(s.getId()).stream().filter(x -> kind.equals(x.getKind())).findFirst().orElse(null);
        if (c == null) {
            c = new PromoSchemaComponentEntity();
            c.setSchemaId(s.getId());
            c.setKind(kind);
            c.setSortOrder("CONDITION".equals(kind) ? 0 : 1);
            c.setConfig(PromoJson.write(cfg));
            c.setAuthoringValues(PromoJson.write(authoring));
            c.setCreatedId(actorId);
            schemaComponents.create(c);
        } else {
            c.setConfig(PromoJson.write(cfg));
            c.setAuthoringValues(PromoJson.write(authoring));
            c.setUpdatedId(actorId);
            c.touch();
            schemaComponents.update(c);
        }
        return c;
    }

    private Map<String, Object> schemaComponentDto(PromoSchemaComponentEntity c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", String.valueOf(c.getId()));
        dto.put("tenantCode", TENANT);
        dto.put("promotionDraftSchemaId", String.valueOf(c.getSchemaId()));
        dto.put("componentKind", c.getKind());
        dto.put("sortOrder", c.getSortOrder());
        dto.put("config", PromoJson.readMap(c.getConfig()));
        dto.put("authoringValues", PromoJson.readMap(c.getAuthoringValues()));
        return dto;
    }

    // ================================================================== MÃ VOUCHER

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> voucherAuthoring(String draftId, String schemaId) {
        PromoDraftEntity d = draft(draftId);
        PromoSchemaEntity s = voucherSchema(d, schemaId);
        PromoVoucherCodeEntity v = voucherOf(s.getId());
        boolean editable = isEditable(d);
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("schema", PromotionAuthoringCatalog.voucherCollectionSchema());
        collection.put("uiSchema", null);
        Map<String, Object> collValues = new LinkedHashMap<>();
        if (v != null) {
            collValues.put("imageUrl", v.getImageUrl() == null ? "" : v.getImageUrl());
            putIfPresent(collValues, "issuanceStartsAt", v.getIssuanceStartsAt());
            putIfPresent(collValues, "issuanceEndsAt", v.getIssuanceEndsAt());
            putIfPresent(collValues, "usageStartsAt", v.getUsageStartsAt());
            putIfPresent(collValues, "usageEndsAt", v.getUsageEndsAt());
        }
        collection.put("values", collValues);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("schema", PromotionAuthoringCatalog.voucherItemSchema());
        item.put("uiSchema", null);
        item.put("defaultValues", PromotionAuthoringCatalog.voucherDefaultItem());
        List<Map<String, Object>> items = new ArrayList<>();
        if (v != null) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("code", v.getCode());
            values.put("status", v.getStatus());
            values.put("visibilityDistributionPolicy", policyOf(v));
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("id", String.valueOf(v.getId()));
            it.put("values", values);
            it.put("requiresSave", false);
            items.add(it);
        }
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("minItems", 1);
        caps.put("maxItems", 1);
        caps.put("canAdd", editable && v == null);
        caps.put("canEdit", editable);
        caps.put("canRemove", false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authoringContractVersion", PromotionAuthoringCatalog.CONTRACT);
        out.put("editorType", "COLLECTION");
        out.put("configType", "VOUCHER_CODE");
        out.put("configVersion", PromotionAuthoringCatalog.VERSION);
        out.put("collection", collection);
        out.put("item", item);
        out.put("items", items);
        out.put("capabilities", caps);
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> voucherCodes(String draftId, String schemaId) {
        PromoDraftEntity d = draft(draftId);
        return voucherListDto(voucherSchema(d, schemaId));
    }

    private Map<String, Object> voucherListDto(PromoSchemaEntity s) {
        PromoVoucherCodeEntity v = voucherOf(s.getId());
        return Map.of("items", v == null ? List.of() : List.of(voucherDto(v)));
    }

    @Transactional
    @Override
    public Map<String, Object> upsertVoucherCode(String draftId, String schemaId, Map<String, Object> body, Long actorId) {
        PromoDraftEntity d = editableDraft(draftId);
        PromoSchemaEntity s = voucherSchema(d, schemaId);
        PromoVoucherCodeEntity v = voucherOf(s.getId());
        boolean creating = v == null;
        if (creating) {
            v = new PromoVoucherCodeEntity();
            v.setSchemaId(s.getId());
            v.setCreatedId(actorId);
        }
        String code = DiscountRules.voucherCode(PromoJson.str(body.get("code")));
        for (PromoVoucherCodeEntity other : voucherCodes.findAll(Sort.unsorted())) {
            if (code.equals(other.getNormalizedCode()) && !other.getSchemaId().equals(s.getId())) {
                throw BusinessException.conflict("DUPLICATE_CODE", "Mã voucher đã tồn tại: " + code, code);
            }
        }
        VoucherEntity legacy = vouchers.findByCode(code);
        if (legacy != null && !legacy.getId().equals(v.getLegacyVoucherId())) {
            throw BusinessException.conflict("DUPLICATE_CODE", "Mã voucher đã tồn tại: " + code, code);
        }
        v.setCode(code);
        v.setNormalizedCode(code);
        if (body.containsKey("status") || creating) {
            String st = PromoJson.str(body.get("status"));
            st = st == null || st.isEmpty() ? "ACTIVE" : st;
            if (!VOUCHER_STATUSES.contains(st)) {
                throw PromoJson.invalid("Trạng thái mã chỉ nhận ACTIVE, INACTIVE hoặc DISABLED", "status");
            }
            v.setStatus(st);
        }
        if (body.containsKey("imageUrl")) {
            String img = PromoJson.str(body.get("imageUrl"));
            if (img != null && img.length() > 500) {
                throw PromoJson.invalid("Đường dẫn ảnh tối đa 500 ký tự", "imageUrl");
            }
            v.setImageUrl(img == null || img.isEmpty() ? null : img);
        }
        if (body.containsKey("issuanceStartsAt")) {
            v.setIssuanceStartsAt(PromoJson.date(body.get("issuanceStartsAt"), "issuanceStartsAt"));
        }
        if (body.containsKey("issuanceEndsAt")) {
            v.setIssuanceEndsAt(PromoJson.date(body.get("issuanceEndsAt"), "issuanceEndsAt"));
        }
        if (body.containsKey("usageStartsAt")) {
            v.setUsageStartsAt(PromoJson.date(body.get("usageStartsAt"), "usageStartsAt"));
        }
        if (body.containsKey("usageEndsAt")) {
            v.setUsageEndsAt(PromoJson.date(body.get("usageEndsAt"), "usageEndsAt"));
        }
        checkWindow(v.getIssuanceStartsAt(), v.getIssuanceEndsAt(), "issuanceEndsAt");
        checkWindow(v.getUsageStartsAt(), v.getUsageEndsAt(), "usageEndsAt");
        if (body.containsKey("visibilityDistributionPolicy") || creating) {
            v.setPolicy(PromoJson.write(validatePolicy(PromoJson.asMap(body.get("visibilityDistributionPolicy")))));
        }
        if (creating) {
            voucherCodes.create(v);
        } else {
            v.setUpdatedId(actorId);
            v.touch();
            voucherCodes.update(v);
        }
        return voucherDto(v);
    }

    @Transactional
    @Override
    public void deleteVoucherCode(String draftId, String schemaId, String voucherCodeId) {
        PromoDraftEntity d = editableDraft(draftId);
        PromoSchemaEntity s = voucherSchema(d, schemaId);
        PromoVoucherCodeEntity v = voucherOf(s.getId());
        if (v == null || !String.valueOf(v.getId()).equals(voucherCodeId)) {
            throw new ResourceNotFoundException("Mã voucher", "VoucherCode", voucherCodeId);
        }
        voucherCodes.delete(v);
    }

    private static void checkWindow(Date from, Date to, String field) {
        if (from != null && to != null && !to.after(from)) {
            throw PromoJson.invalid("Thời gian kết thúc phải sau thời gian bắt đầu", field);
        }
    }

    private Map<String, Object> validatePolicy(Map<String, Object> raw) {
        Map<String, Object> p = PromotionAuthoringCatalog.defaultPolicy();
        if (raw == null) {
            return p;
        }
        String vis = PromoJson.str(raw.get("visibilityType"));
        vis = vis == null || vis.isEmpty() ? "PUBLIC" : vis;
        if (!"PUBLIC".equals(vis) && !"PRIVATE".equals(vis)) {
            throw PromoJson.invalid("visibilityType chỉ nhận PUBLIC hoặc PRIVATE", "visibilityDistributionPolicy.visibilityType");
        }
        List<String> channels = PromoJson.strings(raw.get("distributionChannels"));
        for (String c : channels) {
            if (!Set.of("ZNS", "EMAIL", "OTHER").contains(c)) {
                throw PromoJson.invalid("Kênh phân phối không hợp lệ: " + c, "visibilityDistributionPolicy.distributionChannels");
            }
        }
        if ("PRIVATE".equals(vis) && channels.isEmpty()) {
            throw PromoJson.invalid("Voucher riêng tư cần ít nhất 1 kênh phân phối", "visibilityDistributionPolicy.distributionChannels");
        }
        p.put("visibilityType", vis);
        p.put("distributionChannels", "PUBLIC".equals(vis) ? List.of() : new ArrayList<>(new LinkedHashSet<>(channels)));
        return p;
    }

    private Map<String, Object> policyOf(PromoVoucherCodeEntity v) {
        Map<String, Object> p = PromoJson.readMap(v.getPolicy());
        return p.isEmpty() ? PromotionAuthoringCatalog.defaultPolicy() : p;
    }

    Map<String, Object> voucherDto(PromoVoucherCodeEntity v) {
        PromoSchemaEntity s = schemas.findOne(v.getSchemaId());
        PromoDraftEntity d = s == null ? null : drafts.findOne(s.getDraftId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(v.getId()));
        m.put("tenantCode", TENANT);
        m.put("promotionDraftSchemaId", String.valueOf(v.getSchemaId()));
        m.put("code", v.getCode());
        m.put("normalizedCode", v.getNormalizedCode());
        m.put("codeMask", mask(v.getCode()));
        m.put("status", v.getStatus());
        m.put("imageUrl", v.getImageUrl());
        m.put("issuanceStartsAt", PromoJson.iso(v.getIssuanceStartsAt() != null ? v.getIssuanceStartsAt() : d == null ? null : d.getStartsAt()));
        m.put("issuanceEndsAt", PromoJson.iso(v.getIssuanceEndsAt() != null ? v.getIssuanceEndsAt() : d == null ? null : d.getEndsAt()));
        m.put("usageStartsAt", PromoJson.iso(v.getUsageStartsAt() != null ? v.getUsageStartsAt() : d == null ? null : d.getStartsAt()));
        m.put("usageEndsAt", PromoJson.iso(v.getUsageEndsAt() != null ? v.getUsageEndsAt() : d == null ? null : d.getEndsAt()));
        m.put("visibilityDistributionPolicy", policyOf(v));
        m.put("createdAt", PromoJson.iso(v.getCreatedDate()));
        m.put("updatedAt", PromoJson.iso(v.getUpdatedDate() != null ? v.getUpdatedDate() : v.getCreatedDate()));
        return m;
    }

    static String mask(String code) {
        if (code == null || code.length() < 5) {
            return code == null ? null : "*".repeat(code.length());
        }
        return code.substring(0, 2) + "*".repeat(code.length() - 4) + code.substring(code.length() - 2);
    }

    private static void putIfPresent(Map<String, Object> m, String k, Date v) {
        if (v != null) {
            m.put(k, PromoJson.iso(v));
        }
    }

    // ================================================================== PHÁT HÀNH

    @Transactional
    @Override
    public Map<String, Object> publishDraft(String draftId, Long actorId) {
        PromoDraftEntity d = draft(draftId);
        if (!"EDITING".equals(d.getStatus())) {
            throw BusinessException.conflict("PROMOTION_DRAFT_NOT_EDITABLE", "Chiến dịch không ở trạng thái có thể phát hành");
        }
        List<String> reasons = readinessReasons(d);
        if (!reasons.isEmpty()) {
            throw BusinessException.badRequest("PROMOTION_DRAFT_NOT_READY", "Chiến dịch chưa đủ điều kiện phát hành: " + String.join("; ", reasons));
        }
        Date now = new Date();
        if (!d.getEndsAt().after(now)) {
            throw PromoJson.invalid("Thời gian kết thúc của chiến dịch đã qua", "endsAt");
        }
        List<PromoDraftComponentEntity> comps = componentsOf(d.getId());
        Map<String, Object> target = PromoJson.readMap(comps.stream().filter(c -> "TARGET".equals(c.getKind())).findFirst().orElseThrow().getConfig());
        List<PromoSchemaEntity> enabled = schemasOf(d.getId()).stream().filter(s -> "ENABLED".equals(s.getStatus())).toList();

        Set<Long> productIds = null;
        if (enabled.stream().anyMatch(s -> !"VOUCHER".equals(s.getSchemaType()))) {
            productIds = resolveProductIds(target);
            if (productIds.isEmpty()) {
                throw BusinessException.badRequest("PROMOTION_TARGET_EMPTY", "Không có sản phẩm nào khớp đối tượng áp dụng của chiến dịch");
            }
        }

        PromoPublishedEntity pub = new PromoPublishedEntity();
        pub.setDraftId(d.getId());
        pub.setName(d.getName());
        pub.setDescription(d.getDescription());
        pub.setStatus("ACTIVE");
        pub.setStartsAt(d.getStartsAt());
        pub.setEndsAt(d.getEndsAt());
        pub.setPublishedAt(now);
        pub.setCreatedId(actorId);
        published.create(pub);

        int schemaComponentCount = 0;
        for (PromoSchemaEntity s : enabled) {
            Date from = latest(d.getStartsAt(), s.getSchemaStartsAt());
            Date to = earliest(d.getEndsAt(), s.getSchemaEndsAt());
            List<PromoSchemaComponentEntity> sc = schemaCompsOf(s.getId());
            schemaComponentCount += sc.size();
            Map<String, Object> benefit = PromoJson.readMap(sc.stream().filter(c -> "BENEFIT".equals(c.getKind())).findFirst().orElseThrow().getConfig());
            if ("VOUCHER".equals(s.getSchemaType())) {
                materializeVoucher(d, s, sc, benefit, target, from, to, actorId);
            } else {
                materializePromotion(d, s, benefit, productIds, target, from, to, actorId);
            }
        }
        d.setStatus("PUBLISHED");
        d.setPublishedId(pub.getId());
        d.setUpdatedId(actorId);
        d.touch();
        drafts.update(d);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("components", comps.size());
        counts.put("schemas", enabled.size());
        counts.put("schemaComponents", schemaComponentCount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publishedPromotion", publishedBrief(pub));
        out.put("snapshotId", String.valueOf(pub.getId()));
        out.put("snapshotPayloadHash", sha256(PromoJson.write(Map.of("draft", draftDto(d), "components", comps.stream().map(c -> c.getConfig()).toList(),
                "schemas", enabled.stream().map(s -> s.getId() + ":" + s.getSchemaType()).toList()))));
        out.put("componentCounts", counts);
        return out;
    }

    private void materializePromotion(PromoDraftEntity d, PromoSchemaEntity s, Map<String, Object> benefit, Set<Long> productIds,
                                      Map<String, Object> target, Date from, Date to, Long actorId) {
        PromotionEntity p = new PromotionEntity();
        p.setName(trunc(d.getName() + " - " + s.getName(), 200));
        String type = String.valueOf(benefit.get("type"));
        if ("FLAT_PRICE".equals(type)) {
            p.setDiscountType(PromotionEntity.FLAT_PRICE);
            p.setDiscountValue(PromoJson.num(benefit.get("fixedPriceAmountMinor"), "fixedPriceAmountMinor"));
        } else {
            boolean percent = "PERCENT".equals(benefit.get("discountKind"));
            p.setDiscountType(percent ? PromotionEntity.PERCENT : PromotionEntity.FIXED);
            p.setDiscountValue(PromoJson.num(benefit.get("discountValue"), "discountValue"));
            Map<String, Object> max = PromoJson.asMap(benefit.get("maxDiscountAmount"));
            if (percent && max != null) {
                p.setMaxDiscountAmount(PromoJson.num(max.get("amountMinor"), "maxDiscountAmount"));
            }
        }
        p.setStartsAt(from);
        p.setEndsAt(to);
        p.setStatus(PromotionEntity.PUBLISHED);
        p.setCreatedId(actorId);
        promotions.create(p);
        for (Long productId : productIds) {
            promotions.addProduct(p.getId(), productId);
        }
        // Phạm vi SKU: engine giá tính theo (sản phẩm, SKU) nên SKU anh em không được chọn KHÔNG bị giảm giá.
        // MANUAL = đúng các SKU chọn; mọi loại = trừ excludedSkuIds. CATEGORY/COLLECTION/ALL không cần INCLUDE (cả sản phẩm).
        Set<Long> include = new LinkedHashSet<>();
        Set<Long> exclude = new LinkedHashSet<>();
        for (String sid : PromoJson.strings(target.get("excludedSkuIds"))) {
            exclude.add(Long.valueOf(sid));
        }
        if ("MANUAL".equals(String.valueOf(target.get("type")))) {
            for (String sid : PromoJson.strings(target.get("skuIds"))) {
                Long id = Long.valueOf(sid);
                if (!exclude.contains(id)) {
                    include.add(id);
                }
            }
        }
        for (Long skuId : include) {
            promotions.addScopeSku(p.getId(), "INCLUDE", skuId);
        }
        for (Long skuId : exclude) {
            promotions.addScopeSku(p.getId(), "EXCLUDE", skuId);
        }
        s.setLegacyPromotionId(p.getId());
        schemas.update(s);
    }

    private void materializeVoucher(PromoDraftEntity d, PromoSchemaEntity s, List<PromoSchemaComponentEntity> sc, Map<String, Object> benefit,
                                    Map<String, Object> target, Date from, Date to, Long actorId) {
        PromoVoucherCodeEntity code = voucherOf(s.getId());
        if (vouchers.findByCode(code.getNormalizedCode()) != null) {
            throw BusinessException.conflict("DUPLICATE_CODE", "Mã voucher đã tồn tại: " + code.getNormalizedCode(), code.getNormalizedCode());
        }
        VoucherEntity v = new VoucherEntity();
        v.setCode(code.getNormalizedCode());
        boolean percent = "PERCENT".equals(benefit.get("discountKind"));
        v.setDiscountType(percent ? VoucherEntity.PERCENT : VoucherEntity.FIXED);
        v.setDiscountValue(PromoJson.num(benefit.get("discountValue"), "discountValue"));
        Map<String, Object> max = PromoJson.asMap(benefit.get("maxDiscountAmount"));
        if (percent && max != null) {
            v.setMaxDiscountAmount(PromoJson.num(max.get("amountMinor"), "maxDiscountAmount"));
        }
        BigDecimal min = BigDecimal.ZERO;
        for (PromoSchemaComponentEntity c : sc) {
            if ("CONDITION".equals(c.getKind())) {
                Map<String, Object> cfg = PromoJson.readMap(c.getConfig());
                if ("TARGET_SUBTOTAL".equals(cfg.get("type"))) {
                    min = PromoJson.num(cfg.get("amountMinor"), "amountMinor");
                }
            }
        }
        v.setMinOrderValue(min);
        v.setStartsAt(latest(from, code.getUsageStartsAt()));
        v.setEndsAt(earliest(to, code.getUsageEndsAt()));
        if (!v.getEndsAt().after(v.getStartsAt())) {
            throw PromoJson.invalid("Thời gian sử dụng của mã voucher nằm ngoài thời gian chiến dịch", "usageEndsAt");
        }
        v.setStatus("ACTIVE".equals(code.getStatus()) ? VoucherEntity.PUBLISHED : VoucherEntity.DRAFT);
        v.setCreatedId(actorId);
        // Phạm vi SKU của đối tượng áp dụng: lưu cùng voucher để checkout/báo giá/tạo đơn chỉ giảm trên dòng đủ điều kiện
        // (voucher của SKU A không được giảm cho giỏ chỉ có SKU B). Chụp tại thời điểm phát hành, giống khuyến mãi sản phẩm.
        Set<Long> include = new LinkedHashSet<>();
        Set<Long> exclude = new LinkedHashSet<>();
        voucherScope(target, include, exclude);
        vouchers.create(v);
        for (Long skuId : include) {
            vouchers.addScopeSku(v.getId(), "INCLUDE", skuId);
        }
        for (Long skuId : exclude) {
            vouchers.addScopeSku(v.getId(), "EXCLUDE", skuId);
        }
        code.setLegacyVoucherId(v.getId());
        voucherCodes.update(code);
    }

    /**
     * Quy đổi đối tượng áp dụng thành phạm vi SKU: ALL = không giới hạn (chỉ có EXCLUDE nếu loại trừ SKU),
     * MANUAL = đúng các SKU chọn, CATEGORY/COLLECTION = mọi SKU còn bán của các sản phẩm khớp. Đối tượng giới hạn mà
     * không khớp SKU nào -> PROMOTION_TARGET_EMPTY (tránh voucher "không giới hạn" do danh sách rỗng).
     */
    private void voucherScope(Map<String, Object> target, Set<Long> include, Set<Long> exclude) {
        String type = String.valueOf(target.get("type"));
        Set<Long> excluded = new LinkedHashSet<>();
        for (String sid : PromoJson.strings(target.get("excludedSkuIds"))) {
            excluded.add(Long.valueOf(sid));
        }
        exclude.addAll(excluded);
        if ("ALL".equals(type)) {
            return;
        }
        if ("MANUAL".equals(type)) {
            for (String sid : PromoJson.strings(target.get("skuIds"))) {
                ProductSkuEntity sku = skus.findOne(Long.valueOf(sid));
                if (sku != null && sku.getDeletedDate() == null) {
                    include.add(sku.getId());
                }
            }
        } else {
            Set<Long> productIds = resolveProductIds(target);
            for (ProductSkuEntity sku : skus.findAll(Sort.unsorted())) {
                if (sku.getDeletedDate() == null && productIds.contains(sku.getProductId())) {
                    include.add(sku.getId());
                }
            }
        }
        include.removeAll(excluded);
        if (include.isEmpty()) {
            throw BusinessException.badRequest("PROMOTION_TARGET_EMPTY",
                    "Không có sản phẩm nào khớp đối tượng áp dụng của voucher trong chiến dịch");
        }
    }

    // ================================================================== ĐÃ PHÁT HÀNH

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publishedDetail(String promotionId) {
        PromoPublishedEntity pub = pub(promotionId);
        Map<String, Object> m = publishedBrief(pub);
        m.put("endedAt", PromoJson.iso(pub.getEndedAt()));
        m.put("createdAt", PromoJson.iso(pub.getCreatedDate()));
        m.put("updatedAt", PromoJson.iso(pub.getUpdatedDate() != null ? pub.getUpdatedDate() : pub.getCreatedDate()));
        return m;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publishedQuotaStatistics(String promotionId) {
        PromoPublishedEntity pub = pub(promotionId);
        // Engine hiện tại không có hạn mức số lượng theo hình thức nên không có dòng thống kê nào.
        return Map.of("promotionId", String.valueOf(pub.getId()), "quotaStatistics", List.of());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publishedComponents(String promotionId) {
        return componentsDto(pub(promotionId).getDraftId());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publishedSchemas(String promotionId) {
        PromoPublishedEntity pub = pub(promotionId);
        PromoDraftEntity d = drafts.findOne(pub.getDraftId());
        return Map.of("items", schemasOf(d.getId()).stream().map(s -> schemaDto(s, d, pub)).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publishedSchemaComponents(String promotionId, String schemaId) {
        PromoPublishedEntity pub = pub(promotionId);
        return schemaComponentsDto(schema(drafts.findOne(pub.getDraftId()), schemaId));
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publishedVoucherCodes(String promotionId, String schemaId) {
        PromoPublishedEntity pub = pub(promotionId);
        return voucherListDto(voucherSchema(drafts.findOne(pub.getDraftId()), schemaId));
    }

    @Transactional
    @Override
    public Map<String, Object> pause(String promotionId, Long actorId) {
        PromoPublishedEntity pub = pub(promotionId);
        String cur = publishedStatus(pub);
        if (!"ACTIVE".equals(cur) && !"SCHEDULED".equals(cur)) {
            throw BusinessException.conflict("PROMOTION_INVALID_STATE", "Chỉ tạm dừng được chiến dịch đang chạy hoặc đã lên lịch");
        }
        pub.setStatus("PAUSED");
        return lifecycle(pub, actorId, false);
    }

    @Transactional
    @Override
    public Map<String, Object> resume(String promotionId, Long actorId) {
        PromoPublishedEntity pub = pub(promotionId);
        if (!"PAUSED".equals(pub.getStatus())) {
            throw BusinessException.conflict("PROMOTION_INVALID_STATE", "Chỉ tiếp tục được chiến dịch đang tạm dừng");
        }
        pub.setStatus("ACTIVE");
        return lifecycle(pub, actorId, true);
    }

    @Transactional
    @Override
    public Map<String, Object> end(String promotionId, Long actorId) {
        PromoPublishedEntity pub = pub(promotionId);
        String cur = publishedStatus(pub);
        if ("ENDED".equals(cur) || "ARCHIVED".equals(cur)) {
            throw BusinessException.conflict("PROMOTION_INVALID_STATE", "Chiến dịch đã kết thúc");
        }
        pub.setStatus("ENDED");
        pub.setEndedAt(new Date());
        return lifecycle(pub, actorId, false);
    }

    private Map<String, Object> lifecycle(PromoPublishedEntity pub, Long actorId, boolean active) {
        pub.setUpdatedId(actorId);
        pub.touch();
        published.update(pub);
        setLegacyActive(pub.getDraftId(), active, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(pub.getId()));
        m.put("status", publishedStatus(pub));
        m.put("updatedAt", PromoJson.iso(pub.getUpdatedDate()));
        return m;
    }

    /** Bật/tắt các khuyến mãi + voucher cũ đã hiện thực hoá (onlySchemaId != null: chỉ một hình thức). */
    private void setLegacyActive(Long draftId, boolean active, Long onlySchemaId) {
        for (PromoSchemaEntity s : schemasOf(draftId)) {
            if (onlySchemaId != null && !onlySchemaId.equals(s.getId())) {
                continue;
            }
            boolean on = active && s.getUnpublishedAt() == null && "ENABLED".equals(s.getStatus());
            if (s.getLegacyPromotionId() != null) {
                PromotionEntity p = promotions.findOne(s.getLegacyPromotionId());
                if (p != null) {
                    p.setStatus(on ? PromotionEntity.PUBLISHED : PromotionEntity.DRAFT);
                    p.touch();
                    promotions.update(p);
                }
            }
            PromoVoucherCodeEntity code = voucherOf(s.getId());
            if (code != null && code.getLegacyVoucherId() != null) {
                VoucherEntity v = vouchers.findOne(code.getLegacyVoucherId());
                if (v != null) {
                    v.setStatus(on && "ACTIVE".equals(code.getStatus()) ? VoucherEntity.PUBLISHED : VoucherEntity.DRAFT);
                    v.touch();
                    vouchers.update(v);
                }
            }
        }
    }

    @Transactional
    @Override
    public Map<String, Object> unpublishSchema(String promotionId, String schemaId, Long actorId) {
        PromoPublishedEntity pub = pub(promotionId);
        PromoDraftEntity d = drafts.findOne(pub.getDraftId());
        PromoSchemaEntity s = schema(d, schemaId);
        if (s.getUnpublishedAt() != null) {
            throw BusinessException.conflict("PROMOTION_INVALID_STATE", "Hình thức khuyến mãi này đã được gỡ phát hành");
        }
        s.setUnpublishedAt(new Date());
        s.setUpdatedId(actorId);
        s.touch();
        schemas.update(s);
        setLegacyActive(d.getId(), false, s.getId());
        return schemaDto(s, d, pub);
    }

    // ================================================================== TỔNG QUAN

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> overview(Map<String, String> params) {
        int page = Math.max(1, intParam(params.get("page"), 1));
        int limit = Math.min(200, Math.max(1, intParam(params.get("limit"), 20)));
        boolean includeArchived = "true".equalsIgnoreCase(params.get("includeArchived"));
        String q = params.getOrDefault("search", "").trim().toLowerCase(Locale.ROOT);
        String source = params.getOrDefault("source", "").trim();
        String draftStatus = params.getOrDefault("draftStatus", "").trim();
        String promotionStatus = params.getOrDefault("promotionStatus", "").trim();
        String schemaType = params.getOrDefault("schemaType", "").trim();
        if (!draftStatus.isEmpty() && !DRAFT_STATUSES.contains(draftStatus) || !promotionStatus.isEmpty() && !PUBLISHED_STATUSES.contains(promotionStatus)) {
            throw PromoJson.invalid("Bộ lọc trạng thái không hợp lệ");
        }
        Date[] window = timeWindow(params.get("timeFilter"), params.get("timeFrom"), params.get("timeTo"));

        List<Object[]> rows = new ArrayList<>(); // {Date createdAt, Map item}
        Map<String, Integer> draftCounts = new LinkedHashMap<>();
        Map<String, Integer> pubCounts = new LinkedHashMap<>();
        for (String s : List.of("EDITING", "READY_TO_PUBLISH", "PUBLISHED", "DISCARDED")) {
            draftCounts.put(s, 0);
        }
        for (String s : List.of("SCHEDULED", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED")) {
            pubCounts.put(s, 0);
        }
        int draftTotal = 0;
        int pubTotal = 0;
        for (PromoDraftEntity d : drafts.findAll(Sort.unsorted())) {
            if ("PUBLISHED".equals(d.getStatus())) {
                continue; // hiển thị bằng bản đã phát hành
            }
            String eff = effectiveStatus(d);
            boolean discarded = "DISCARDED".equals(eff);
            if (discarded && !includeArchived && !"DISCARDED".equals(draftStatus)) {
                continue;
            }
            List<PromoSchemaEntity> ss = schemasOf(d.getId());
            if (!matches(q, d.getName(), schemaType, ss, window, d.getCreatedDate())) {
                continue;
            }
            draftCounts.merge(eff, 1, Integer::sum);
            if ((!source.isEmpty() && !"DRAFT".equals(source)) || (!draftStatus.isEmpty() && !draftStatus.equals(eff)) || !promotionStatus.isEmpty()) {
                continue;
            }
            draftTotal++;
            Map<String, Object> item = overviewItem("DRAFT", String.valueOf(d.getId()), d.getName(), d.getDescription(), d.getStartsAt(), d.getEndsAt(), d.getCreatedDate(), ss);
            item.put("draftStatus", eff);
            item.put("availableActions", draftActions(eff));
            rows.add(new Object[]{d.getCreatedDate(), item});
        }
        for (PromoPublishedEntity pub : published.findAll(Sort.unsorted())) {
            String eff = publishedStatus(pub);
            boolean archived = "ARCHIVED".equals(eff);
            if (archived && !includeArchived && !"ARCHIVED".equals(promotionStatus)) {
                continue;
            }
            List<PromoSchemaEntity> ss = schemasOf(pub.getDraftId());
            if (!matches(q, pub.getName(), schemaType, ss, window, pub.getCreatedDate())) {
                continue;
            }
            pubCounts.merge(eff, 1, Integer::sum);
            if ((!source.isEmpty() && !"PUBLISHED".equals(source)) || (!promotionStatus.isEmpty() && !promotionStatus.equals(eff)) || !draftStatus.isEmpty()) {
                continue;
            }
            pubTotal++;
            Map<String, Object> item = overviewItem("PUBLISHED", String.valueOf(pub.getId()), pub.getName(), pub.getDescription(), pub.getStartsAt(), pub.getEndsAt(), pub.getCreatedDate(), ss);
            item.put("promotionStatus", eff);
            item.put("availableActions", publishedActions(eff));
            rows.add(new Object[]{pub.getCreatedDate(), item});
        }
        rows.sort((a, b) -> ((Date) b[0]).compareTo((Date) a[0]));
        int from = Math.min((page - 1) * limit, rows.size());
        List<Object> items = new ArrayList<>();
        for (Object[] r : rows.subList(from, Math.min(from + limit, rows.size()))) {
            items.add(r[1]);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("total", rows.size());
        meta.put("page", page);
        meta.put("limit", limit);
        meta.put("draftCount", draftTotal);
        meta.put("publishedCount", pubTotal);
        meta.put("sourceCounts", Map.of("DRAFT", draftCounts.values().stream().mapToInt(Integer::intValue).sum(),
                "PUBLISHED", pubCounts.values().stream().mapToInt(Integer::intValue).sum()));
        meta.put("statusCounts", Map.of("draft", draftCounts, "published", pubCounts));
        return Map.of("items", items, "meta", meta);
    }

    private boolean matches(String q, String name, String schemaType, List<PromoSchemaEntity> ss, Date[] window, Date createdAt) {
        if (!q.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(q))) {
            return false;
        }
        if (!schemaType.isEmpty() && ss.stream().noneMatch(s -> schemaType.equals(s.getSchemaType()))) {
            return false;
        }
        return (window[0] == null || !createdAt.before(window[0])) && (window[1] == null || !createdAt.after(window[1]));
    }

    private Map<String, Object> overviewItem(String source, String id, String name, String desc, Date from, Date to, Date created, List<PromoSchemaEntity> ss) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("source", source);
        m.put("name", name);
        m.put("description", desc);
        m.put("startsAt", PromoJson.iso(from));
        m.put("endsAt", PromoJson.iso(to));
        m.put("createdAt", PromoJson.iso(created));
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (PromoSchemaEntity s : ss) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", s.getName());
            x.put("schemaType", s.getSchemaType());
            x.put("benefitSummary", benefitSummary(s));
            summaries.add(x);
        }
        m.put("schemas", summaries);
        m.put("salesChannels", List.of("ONLINE"));
        return m;
    }

    private static List<String> draftActions(String status) {
        List<String> a = new ArrayList<>(List.of("VIEW"));
        if ("EDITING".equals(status) || "READY_TO_PUBLISH".equals(status)) {
            a.add("EDIT");
            a.add("DELETE");
        }
        if ("READY_TO_PUBLISH".equals(status)) {
            a.add("PUBLISH");
        }
        return a;
    }

    private static List<String> publishedActions(String status) {
        List<String> a = new ArrayList<>(List.of("VIEW"));
        if ("ACTIVE".equals(status) || "SCHEDULED".equals(status)) {
            a.add("PAUSE");
        }
        if ("PAUSED".equals(status)) {
            a.add("RESUME");
        }
        if ("ACTIVE".equals(status) || "SCHEDULED".equals(status) || "PAUSED".equals(status)) {
            a.add("END");
        }
        return a;
    }

    private String benefitSummary(PromoSchemaEntity s) {
        PromoSchemaComponentEntity b = schemaCompsOf(s.getId()).stream().filter(c -> "BENEFIT".equals(c.getKind())).findFirst().orElse(null);
        return PromotionAuthoringCatalog.benefitSummary(b == null ? null : PromoJson.readMap(b.getConfig()), s.getSchemaType());
    }

    private static Date[] timeWindow(String filter, String from, String to) {
        if (filter == null || filter.isBlank()) {
            return new Date[]{null, null};
        }
        ZonedDateTime now = ZonedDateTime.now(PromoJson.ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate start;
        LocalDate end = today;
        switch (filter.trim().toUpperCase(Locale.ROOT)) {
            case "TODAY" -> start = today;
            case "YESTERDAY" -> {
                start = today.minusDays(1);
                end = start;
            }
            case "LAST_7_DAYS" -> start = today.minusDays(6);
            case "LAST_30_DAYS" -> start = today.minusDays(29);
            case "LAST_90_DAYS" -> start = today.minusDays(89);
            case "THIS_MONTH" -> start = today.withDayOfMonth(1);
            case "LAST_MONTH" -> {
                start = today.minusMonths(1).withDayOfMonth(1);
                end = today.withDayOfMonth(1).minusDays(1);
            }
            case "CUSTOM" -> {
                Date f = PromoJson.date(from, "timeFrom");
                Date t = PromoJson.date(to, "timeTo");
                return new Date[]{f, t};
            }
            default -> throw PromoJson.invalid("timeFilter không hợp lệ", "timeFilter");
        }
        return new Date[]{Date.from(start.atStartOfDay(PromoJson.ZONE).toInstant()), Date.from(end.plusDays(1).atStartOfDay(PromoJson.ZONE).toInstant().minusMillis(1))};
    }

    private static int intParam(String s, int def) {
        try {
            return s == null || s.isBlank() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    // ================================================================== HELPER

    PromoDraftEntity draft(String id) {
        Long key = PromoJson.id(id);
        PromoDraftEntity d = key == null ? null : drafts.findOne(key);
        if (d == null) {
            throw new ResourceNotFoundException("Chiến dịch khuyến mãi", "PromotionDraft", id);
        }
        return d;
    }

    private PromoDraftEntity editableDraft(String id) {
        PromoDraftEntity d = draft(id);
        if (!isEditable(d)) {
            throw BusinessException.conflict("PROMOTION_DRAFT_NOT_EDITABLE", "Chiến dịch đã phát hành hoặc đã huỷ, không thể chỉnh sửa");
        }
        return d;
    }

    private static boolean isEditable(PromoDraftEntity d) {
        return "EDITING".equals(d.getStatus());
    }

    private PromoPublishedEntity pub(String id) {
        Long key = PromoJson.id(id);
        PromoPublishedEntity p = key == null ? null : published.findOne(key);
        if (p == null) {
            throw new ResourceNotFoundException("Chiến dịch đã phát hành", "PublishedPromotion", id);
        }
        return p;
    }

    private PromoPublishedEntity publishedOf(PromoDraftEntity d) {
        return d.getPublishedId() == null ? null : published.findOne(d.getPublishedId());
    }

    private PromoSchemaEntity schema(PromoDraftEntity d, String schemaId) {
        Long key = PromoJson.id(schemaId);
        PromoSchemaEntity s = key == null ? null : schemas.findOne(key);
        if (s == null || !s.getDraftId().equals(d.getId())) {
            throw new ResourceNotFoundException("Hình thức khuyến mãi", "PromotionSchema", schemaId);
        }
        return s;
    }

    private PromoSchemaEntity voucherSchema(PromoDraftEntity d, String schemaId) {
        PromoSchemaEntity s = schema(d, schemaId);
        if (!"VOUCHER".equals(s.getSchemaType())) {
            throw PromoJson.invalid("Chỉ hình thức VOUCHER mới có mã voucher", "schemaId");
        }
        return s;
    }

    List<PromoDraftComponentEntity> componentsOf(Long draftId) {
        return draftComponents.findAll(Sort.unsorted()).stream().filter(c -> draftId.equals(c.getDraftId()))
                .sorted(Comparator.comparingInt(PromoDraftComponentEntity::getSortOrder)).toList();
    }

    List<PromoSchemaEntity> schemasOf(Long draftId) {
        return schemas.findAll(Sort.unsorted()).stream().filter(s -> draftId.equals(s.getDraftId()))
                .sorted(Comparator.comparingInt(PromoSchemaEntity::getSortOrder).thenComparing(PromoSchemaEntity::getId)).toList();
    }

    List<PromoSchemaComponentEntity> schemaCompsOf(Long schemaId) {
        return schemaComponents.findAll(Sort.unsorted()).stream().filter(c -> schemaId.equals(c.getSchemaId()))
                .sorted(Comparator.comparingInt(PromoSchemaComponentEntity::getSortOrder)).toList();
    }

    PromoVoucherCodeEntity voucherOf(Long schemaId) {
        return voucherCodes.findAll(Sort.unsorted()).stream().filter(c -> schemaId.equals(c.getSchemaId())).findFirst().orElse(null);
    }

    private String effectiveStatus(PromoDraftEntity d) {
        if ("EDITING".equals(d.getStatus()) && readinessReasons(d).isEmpty()) {
            return "READY_TO_PUBLISH";
        }
        return d.getStatus();
    }

    /** Lý do chưa phát hành được (rỗng = sẵn sàng) - cùng bộ tiêu chí với màn hình "sẵn sàng phát hành" của FE. */
    List<String> readinessReasons(PromoDraftEntity d) {
        List<String> reasons = new ArrayList<>();
        List<PromoDraftComponentEntity> comps = componentsOf(d.getId());
        if (comps.stream().noneMatch(c -> "TARGET".equals(c.getKind()))) {
            reasons.add("cần cấu hình sản phẩm áp dụng");
        }
        if (comps.stream().noneMatch(c -> "AUDIENCE".equals(c.getKind()))) {
            reasons.add("cần cấu hình đối tượng khách hàng");
        }
        List<PromoSchemaEntity> enabled = schemasOf(d.getId()).stream().filter(s -> "ENABLED".equals(s.getStatus())).toList();
        if (enabled.isEmpty()) {
            reasons.add("cần bật ít nhất một hình thức khuyến mãi");
        }
        for (PromoSchemaEntity s : enabled) {
            List<PromoSchemaComponentEntity> sc = schemaCompsOf(s.getId());
            boolean benefit = sc.stream().anyMatch(c -> "BENEFIT".equals(c.getKind()));
            boolean condition = sc.stream().anyMatch(c -> "CONDITION".equals(c.getKind()));
            if (!benefit) {
                reasons.add("\"" + s.getName() + "\" thiếu quyền lợi");
            }
            if ("VOUCHER".equals(s.getSchemaType())) {
                if (!condition) {
                    reasons.add("\"" + s.getName() + "\" thiếu điều kiện");
                }
                PromoVoucherCodeEntity v = voucherOf(s.getId());
                if (v == null || !"ACTIVE".equals(v.getStatus())) {
                    reasons.add("\"" + s.getName() + "\" cần mã voucher đang kích hoạt");
                }
            }
        }
        return reasons;
    }

    private Map<String, Object> draftDto(PromoDraftEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(d.getId()));
        m.put("tenantCode", TENANT);
        m.put("status", effectiveStatus(d));
        m.put("name", d.getName());
        m.put("description", d.getDescription());
        m.put("startsAt", PromoJson.iso(d.getStartsAt()));
        m.put("endsAt", PromoJson.iso(d.getEndsAt()));
        m.put("createdAt", PromoJson.iso(d.getCreatedDate()));
        m.put("updatedAt", PromoJson.iso(d.getUpdatedDate() != null ? d.getUpdatedDate() : d.getCreatedDate()));
        return m;
    }

    private Map<String, Object> publishedBrief(PromoPublishedEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(p.getId()));
        m.put("tenantCode", TENANT);
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("status", publishedStatus(p));
        m.put("startsAt", PromoJson.iso(p.getStartsAt()));
        m.put("endsAt", PromoJson.iso(p.getEndsAt()));
        m.put("publishedAt", PromoJson.iso(p.getPublishedAt()));
        return m;
    }

    static String publishedStatus(PromoPublishedEntity p) {
        Date now = new Date();
        return switch (p.getStatus()) {
            case "PAUSED" -> "PAUSED";
            case "ENDED" -> "ENDED";
            case "ARCHIVED" -> "ARCHIVED";
            default -> now.before(p.getStartsAt()) ? "SCHEDULED" : now.after(p.getEndsAt()) ? "ENDED" : "ACTIVE";
        };
    }

    private Map<String, Object> schemaDto(PromoSchemaEntity s, PromoDraftEntity d, PromoPublishedEntity pub) {
        Date effFrom = latest(d.getStartsAt(), s.getSchemaStartsAt());
        Date effTo = earliest(d.getEndsAt(), s.getSchemaEndsAt());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("tenantCode", TENANT);
        m.put("promotionDraftId", String.valueOf(s.getDraftId()));
        m.put("sortOrder", s.getSortOrder());
        m.put("name", s.getName());
        m.put("description", s.getDescription());
        m.put("scope", s.getScope());
        m.put("schemaType", s.getSchemaType());
        m.put("costSources", s.getCostSources() == null || s.getCostSources().isEmpty() ? List.of() : List.of(s.getCostSources().split(",")));
        m.put("priority", s.getPriority());
        m.put("status", s.getStatus());
        m.put("displayStatus", displayStatus(s, pub, effFrom, effTo));
        m.put("effectiveStartsAt", PromoJson.iso(effFrom));
        m.put("effectiveEndsAt", PromoJson.iso(effTo));
        m.put("schemaStartsAt", PromoJson.iso(s.getSchemaStartsAt()));
        m.put("schemaEndsAt", PromoJson.iso(s.getSchemaEndsAt()));
        m.put("unpublishedAt", PromoJson.iso(s.getUnpublishedAt()));
        m.put("createdAt", PromoJson.iso(s.getCreatedDate()));
        m.put("updatedAt", PromoJson.iso(s.getUpdatedDate() != null ? s.getUpdatedDate() : s.getCreatedDate()));
        return m;
    }

    private static String displayStatus(PromoSchemaEntity s, PromoPublishedEntity pub, Date from, Date to) {
        if (pub == null) {
            return "DRAFT";
        }
        if (s.getUnpublishedAt() != null || !"ENABLED".equals(s.getStatus())) {
            return "UNPUBLISHED";
        }
        String ps = publishedStatus(pub);
        Date now = new Date();
        if ("ENDED".equals(ps) || now.after(to)) {
            return "ENDED";
        }
        if ("PAUSED".equals(ps)) {
            return "PUBLISHED";
        }
        return now.before(from) ? "SCHEDULED" : "RUNNING";
    }

    private static Date latest(Date a, Date b) {
        return b == null ? a : a.after(b) ? a : b;
    }

    private static Date earliest(Date a, Date b) {
        return b == null ? a : a.before(b) ? a : b;
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Id sản phẩm khớp đối tượng áp dụng của chiến dịch (chụp tại thời điểm phát hành). Khuyến mãi của engine cũ gắn ở mức sản phẩm,
     * nên một SKU được chọn sẽ áp cho cả sản phẩm; SKU loại trừ chỉ có tác dụng khi mọi SKU của sản phẩm đều bị loại. */
    Set<Long> resolveProductIds(Map<String, Object> target) {
        String type = String.valueOf(target.get("type"));
        List<ProductEntity> all = products.findAll(Sort.unsorted()).stream().filter(p -> p.getDeletedDate() == null).toList();
        Set<Long> ids = new LinkedHashSet<>();
        switch (type) {
            case "ALL" -> all.forEach(p -> ids.add(p.getId()));
            case "CATEGORY" -> {
                Set<Long> cats = new HashSet<>();
                for (String c : PromoJson.strings(target.get("categoryIds"))) {
                    cats.add(Long.valueOf(c));
                }
                List<CategoryEntity> allCats = categories.findAll(Sort.unsorted());
                boolean grew = true;
                while (grew) {
                    grew = false;
                    for (CategoryEntity c : allCats) {
                        if (c.getParentId() != null && cats.contains(c.getParentId()) && cats.add(c.getId())) {
                            grew = true;
                        }
                    }
                }
                all.stream().filter(p -> p.getCategoryId() != null && cats.contains(p.getCategoryId())).forEach(p -> ids.add(p.getId()));
            }
            case "COLLECTION" -> {
                Set<Long> alive = new HashSet<>();
                all.forEach(p -> alive.add(p.getId()));
                for (String c : PromoJson.strings(target.get("collectionIds"))) {
                    for (Long pid : collections.findProductIdsByCollectionId(Long.valueOf(c))) {
                        if (alive.contains(pid)) {
                            ids.add(pid);
                        }
                    }
                }
            }
            default -> {
                for (String sid : PromoJson.strings(target.get("skuIds"))) {
                    ProductSkuEntity s = skus.findOne(Long.valueOf(sid));
                    if (s != null && s.getDeletedDate() == null && s.getProductId() != null) {
                        ids.add(s.getProductId());
                    }
                }
            }
        }
        Set<String> excluded = new HashSet<>(PromoJson.strings(target.get("excludedSkuIds")));
        if (!excluded.isEmpty()) {
            Map<Long, List<ProductSkuEntity>> byProduct = new LinkedHashMap<>();
            for (ProductSkuEntity s : skus.findAll(Sort.unsorted())) {
                if (s.getDeletedDate() == null) {
                    byProduct.computeIfAbsent(s.getProductId(), k -> new ArrayList<>()).add(s);
                }
            }
            ids.removeIf(pid -> {
                List<ProductSkuEntity> list = byProduct.getOrDefault(pid, List.of());
                return !list.isEmpty() && list.stream().allMatch(s -> excluded.contains(String.valueOf(s.getId())));
            });
        }
        return ids;
    }
}

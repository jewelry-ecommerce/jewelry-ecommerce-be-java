package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.CmsNavigationBadgeRepo;
import com.pk.core.business.repository.CmsNavigationPublishRepo;
import com.pk.core.business.repository.CmsNavigationRepo;
import com.pk.core.business.repository.CmsPageRepo;
import com.pk.core.business.service.NavigationService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminNavigationReorderRequestDto;
import com.pk.core.model.dto.request.AdminNavigationRequestDto;
import com.pk.core.model.dto.response.NavigationResponseDto;
import com.pk.core.model.dto.response.StorefrontNavigationDto;
import com.pk.core.model.entity.CmsNavigationBadgeEntity;
import com.pk.core.model.entity.CmsNavigationEntity;
import com.pk.core.model.entity.CmsNavigationPublishEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {

    static final int MAX_DEPTH = 4;
    private static final String TENANT = "default";
    private static final Set<String> TYPES = Set.of("NONE", "CATEGORY", "PAGE", "LINK");
    private static final Set<String> DESIGNS = Set.of("TEXT", "IMAGE");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final CmsNavigationRepo navs;
    private final CmsNavigationBadgeRepo badges;
    private final CmsNavigationPublishRepo publishRepo;
    private final CategoryRepo categories;
    private final CmsPageRepo pages;
    private final ObjectMapper mapper;

    // ============================================================ admin: đọc

    @Transactional(readOnly = true)
    @Override
    public List<NavigationResponseDto> tree() {
        List<CmsNavigationEntity> all = layerNodes(CmsNavigationEntity.DRAFT, false);
        Map<Long, List<CmsNavigationBadgeEntity>> bs = badgesByNav(CmsNavigationEntity.DRAFT);
        return buildAdminTree(all, bs, null);
    }

    @Transactional(readOnly = true)
    @Override
    public NavigationResponseDto detail(Long id) {
        CmsNavigationEntity n = requireDraft(id);
        List<CmsNavigationEntity> all = layerNodes(CmsNavigationEntity.DRAFT, false);
        return toAdmin(n, badgesByNav(CmsNavigationEntity.DRAFT), childrenOf(all, n.getId()));
    }

    // ============================================================ admin: ghi

    @Transactional
    @Override
    public NavigationResponseDto create(AdminNavigationRequestDto req, Long actorId) {
        CmsNavigationEntity n = new CmsNavigationEntity();
        n.setLayer(CmsNavigationEntity.DRAFT);
        n.setCreatedId(actorId);
        List<CmsNavigationEntity> live = layerNodes(CmsNavigationEntity.DRAFT, false);
        int depth = 1;
        if (req.getParentId() != null) {
            CmsNavigationEntity parent = requireDraft(req.getParentId());
            if (parent.isArchived()) {
                throw invalid("Nút cha đã được lưu trữ");
            }
            n.setParentId(parent.getId());
            depth = depthOf(parent, live) + 1;
        }
        if (depth > MAX_DEPTH) {
            throw BusinessException.badRequest("NAVIGATION_DEPTH_EXCEEDED", "Menu tối đa " + MAX_DEPTH + " cấp", MAX_DEPTH);
        }
        apply(n, req, true, depth);
        if (req.getSortOrder() == null) {
            Long pid = n.getParentId();
            n.setSortOrder(live.stream().filter(x -> java.util.Objects.equals(x.getParentId(), pid))
                    .mapToInt(CmsNavigationEntity::getSortOrder).max().orElse(-1) + 1);
        }
        navs.create(n);
        applyBadge(n, req, actorId);
        return detail(n.getId());
    }

    @Transactional
    @Override
    public NavigationResponseDto update(Long id, AdminNavigationRequestDto req, Long actorId) {
        CmsNavigationEntity n = requireDraft(id);
        if (n.isArchived()) {
            throw invalid("Nút đã được lưu trữ, không thể sửa");
        }
        int depth = depthOf(n, layerNodes(CmsNavigationEntity.DRAFT, false));
        apply(n, req, false, depth);
        n.setUpdatedId(actorId);
        n.touch();
        navs.update(n);
        applyBadge(n, req, actorId);
        return detail(n.getId());
    }

    @Transactional
    @Override
    public void archive(Long id) {
        CmsNavigationEntity n = requireDraft(id);
        List<CmsNavigationEntity> all = layerNodes(CmsNavigationEntity.DRAFT, true);
        archiveRecursive(n, all);
    }

    private void archiveRecursive(CmsNavigationEntity n, List<CmsNavigationEntity> all) {
        if (!n.isArchived()) {
            n.setArchived(true);
            n.touch();
            navs.update(n);
        }
        for (CmsNavigationEntity c : all) {
            if (n.getId().equals(c.getParentId())) {
                archiveRecursive(c, all);
            }
        }
    }

    @Transactional
    @Override
    public void reorder(AdminNavigationReorderRequestDto req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw invalid("items không được để trống");
        }
        Map<Long, CmsNavigationEntity> byId = new HashMap<>();
        for (AdminNavigationReorderRequestDto.Item it : req.getItems()) {
            if (it.getId() == null || it.getSortOrder() == null || it.getSortOrder() < 0) {
                throw invalid("Mỗi phần tử cần id và sortOrder >= 0");
            }
            byId.put(it.getId(), requireDraft(it.getId()));
        }
        for (AdminNavigationReorderRequestDto.Item it : req.getItems()) {
            CmsNavigationEntity n = byId.get(it.getId());
            n.setSortOrder(it.getSortOrder());
            n.touch();
            navs.update(n);
        }
    }

    // ============================================================ xuất bản

    @Transactional(readOnly = true)
    @Override
    public boolean hasPendingPublish() {
        CmsNavigationPublishEntity p = publishRow();
        String last = p == null || p.getFingerprint() == null ? fingerprint(List.of(), List.of()) : p.getFingerprint();
        return !last.equals(draftFingerprint());
    }

    @Transactional
    @Override
    public boolean publish(Long actorId) {
        // 1) xoá lớp PUBLISHED cũ
        for (CmsNavigationBadgeEntity b : badges.findAll(Sort.unsorted())) {
            if (CmsNavigationEntity.PUBLISHED.equals(b.getLayer())) {
                badges.delete(b);
            }
        }
        for (CmsNavigationEntity n : navs.findAll(Sort.unsorted())) {
            if (CmsNavigationEntity.PUBLISHED.equals(n.getLayer())) {
                navs.delete(n);
            }
        }
        // 2) sao chép nháp (cha trước con)
        List<CmsNavigationEntity> draft = layerNodes(CmsNavigationEntity.DRAFT, false);
        Map<Long, Long> idMap = new HashMap<>();
        copyLevel(draft, null, idMap, actorId);
        Map<Long, List<CmsNavigationBadgeEntity>> bs = badgesByNav(CmsNavigationEntity.DRAFT);
        for (Map.Entry<Long, Long> e : idMap.entrySet()) {
            for (CmsNavigationBadgeEntity b : bs.getOrDefault(e.getKey(), List.of())) {
                CmsNavigationBadgeEntity c = new CmsNavigationBadgeEntity();
                c.setLayer(CmsNavigationEntity.PUBLISHED);
                c.setSourceId(b.getId());
                c.setNavigationId(e.getValue());
                c.setDesignType(b.getDesignType());
                c.setDisplayText(b.getDisplayText());
                c.setImageUrl(b.getImageUrl());
                c.setStyleConfig(b.getStyleConfig());
                c.setStartTime(b.getStartTime());
                c.setEndTime(b.getEndTime());
                c.setCreatedId(actorId);
                badges.create(c);
            }
        }
        // 3) ghi dấu vân tay
        CmsNavigationPublishEntity p = publishRow();
        boolean created = p == null;
        if (created) {
            p = new CmsNavigationPublishEntity();
            p.setCreatedId(actorId);
        }
        p.setFingerprint(draftFingerprint());
        p.setPublishedDate(new Date());
        if (created) {
            publishRepo.create(p);
        } else {
            p.setUpdatedId(actorId);
            p.touch();
            publishRepo.update(p);
        }
        return false;
    }

    private void copyLevel(List<CmsNavigationEntity> draft, Long parentDraftId, Map<Long, Long> idMap, Long actorId) {
        Long parentPublished = parentDraftId == null ? null : idMap.get(parentDraftId);
        List<CmsNavigationEntity> level = draft.stream()
                .filter(x -> java.util.Objects.equals(x.getParentId(), parentDraftId))
                .sorted(NODE_ORDER).toList();
        for (CmsNavigationEntity d : level) {
            CmsNavigationEntity c = new CmsNavigationEntity();
            c.setLayer(CmsNavigationEntity.PUBLISHED);
            c.setSourceId(d.getId());
            c.setParentId(parentPublished);
            c.setLabel(d.getLabel());
            c.setType(d.getType());
            c.setTargetId(d.getTargetId());
            c.setUrl(d.getUrl());
            c.setQueryParams(d.getQueryParams());
            c.setIcon(d.getIcon());
            c.setImage(d.getImage());
            c.setOpenInNewTab(d.isOpenInNewTab());
            c.setSortOrder(d.getSortOrder());
            c.setShowOnMenuPopup(d.isShowOnMenuPopup());
            c.setShowOnPlp(d.isShowOnPlp());
            c.setArchived(false);
            c.setCreatedId(actorId);
            navs.create(c);
            idMap.put(d.getId(), c.getId());
            copyLevel(draft, d.getId(), idMap, actorId);
        }
    }

    @Override
    public Map<String, Object> clearCache() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("tenantCode", TENANT);
        return m;
    }

    // ============================================================ storefront

    @Transactional(readOnly = true)
    @Override
    public StorefrontNavigationDto storefront() {
        List<CmsNavigationEntity> all = layerNodes(CmsNavigationEntity.PUBLISHED, false);
        return new StorefrontNavigationDto(buildPublic(all, badgesByNav(CmsNavigationEntity.PUBLISHED), null, false, false));
    }

    @Transactional(readOnly = true)
    @Override
    public StorefrontNavigationDto plpRail(String categoryId, String rootTargetId, String rootUrl) {
        List<CmsNavigationEntity> all = layerNodes(CmsNavigationEntity.PUBLISHED, false);
        Map<Long, List<CmsNavigationBadgeEntity>> bs = badgesByNav(CmsNavigationEntity.PUBLISHED);
        String target = firstNonBlank(rootTargetId, categoryId);
        String url = rootUrl == null ? "" : rootUrl.trim();
        CmsNavigationEntity root = null;
        if (!target.isEmpty() || !url.isEmpty()) {
            // nút khớp nông nhất (gần gốc nhất) có nút con hiện ở PLP
            root = all.stream()
                    .filter(n -> (!target.isEmpty() && "CATEGORY".equals(n.getType()) && target.equals(n.getTargetId()))
                            || (!url.isEmpty() && url.equals(n.getUrl())))
                    .min(Comparator.comparingInt((CmsNavigationEntity n) -> depthOf(n, all)).thenComparing(NODE_ORDER))
                    .orElse(null);
        }
        Long rootId = root == null ? null : root.getId();
        return new StorefrontNavigationDto(buildPublic(all, bs, rootId, true, false));
    }

    /** Dựng cây công khai từ parentId (null = gốc). plp=true: chỉ giữ nút showOnPlp và tính 'ẩn do cha' theo PLP; ngược lại theo menu popup. */
    private List<StorefrontNavigationDto.Item> buildPublic(List<CmsNavigationEntity> all,
                                                           Map<Long, List<CmsNavigationBadgeEntity>> bs, Long parentId,
                                                           boolean plp, boolean ancestorHidden) {
        List<StorefrontNavigationDto.Item> out = new ArrayList<>();
        for (CmsNavigationEntity n : childrenOf(all, parentId)) {
            if (plp && !n.isShowOnPlp()) {
                continue;
            }
            boolean visible = plp ? n.isShowOnPlp() : n.isShowOnMenuPopup();
            List<StorefrontNavigationDto.Item> kids = buildPublic(all, bs, n.getId(), plp, ancestorHidden || !visible);
            out.add(toPublic(n, kids, bs.getOrDefault(n.getId(), List.of()), ancestorHidden, all));
        }
        return out;
    }

    private StorefrontNavigationDto.Item toPublic(CmsNavigationEntity n, List<StorefrontNavigationDto.Item> kids,
                                                  List<CmsNavigationBadgeEntity> bs, boolean parentHidden,
                                                  List<CmsNavigationEntity> all) {
        Map<String, Object> qp = readMap(n.getQueryParams());
        String type = n.getType();
        StorefrontNavigationDto.LinkTarget lt;
        if ("CATEGORY".equals(type)) {
            lt = new StorefrontNavigationDto.LinkTarget("category", n.getTargetId(), n.getUrl(), qp);
        } else if ("PAGE".equals(type)) {
            lt = new StorefrontNavigationDto.LinkTarget("page", n.getTargetId(), n.getUrl(), qp);
        } else if ("LINK".equals(type)) {
            lt = new StorefrontNavigationDto.LinkTarget("url", null, n.getUrl(), qp);
        } else if (qp != null && !qp.isEmpty()) {
            lt = new StorefrontNavigationDto.LinkTarget("filter", null, null, qp);
        } else {
            lt = new StorefrontNavigationDto.LinkTarget("none", null, null, null);
        }
        boolean hasChildren = !kids.isEmpty();
        boolean linkable = !"NONE".equals(type);
        Date now = new Date();
        CmsNavigationBadgeEntity active = bs.stream()
                .filter(b -> !b.getStartTime().after(now) && (b.getEndTime() == null || now.before(b.getEndTime())))
                .max(Comparator.comparing(CmsNavigationBadgeEntity::getStartTime)).orElse(null);
        StorefrontNavigationDto.Badge badge = active == null ? null : new StorefrontNavigationDto.Badge(
                String.valueOf(active.getSourceId() != null ? active.getSourceId() : active.getId()),
                active.getDesignType(), active.getDisplayText(), active.getImageUrl(), readMap(active.getStyleConfig()),
                iso(active.getStartTime()), iso(active.getEndTime()));
        String parentPublicId = null;
        if (n.getParentId() != null) {
            CmsNavigationEntity p = all.stream().filter(x -> x.getId().equals(n.getParentId())).findFirst().orElse(null);
            parentPublicId = p == null ? null : String.valueOf(p.getSourceId());
        }
        return new StorefrontNavigationDto.Item(String.valueOf(n.getSourceId()), n.getLabel(), type,
                blankToNull(n.getTargetId()), blankToNull(n.getUrl()), qp, blankToNull(n.getIcon()),
                blankToNull(n.getImage()), n.isOpenInNewTab(), n.getSortOrder(), parentPublicId,
                n.isShowOnMenuPopup(), n.isShowOnPlp(), lt, badge, hasChildren, hasChildren && !linkable,
                hasChildren && linkable ? "Xem tất cả" : null, parentHidden, kids);
    }

    // ============================================================ áp dụng request

    private void apply(CmsNavigationEntity n, AdminNavigationRequestDto req, boolean creating, int depth) {
        if (creating || req.getLabel() != null) {
            String label = req.getLabel() == null ? "" : req.getLabel().trim();
            if (label.isEmpty() || label.length() > 150) {
                throw invalid("label bắt buộc, tối đa 150 ký tự");
            }
            n.setLabel(label);
        }
        if (creating || req.getType() != null) {
            String type = req.getType() == null ? "NONE" : req.getType().trim().toUpperCase();
            if (!TYPES.contains(type)) {
                throw invalid("type không hợp lệ: " + req.getType() + " (NONE | CATEGORY | PAGE | LINK)");
            }
            n.setType(type);
        }
        if (req.getTargetId() != null) {
            n.setTargetId(blankToNull(req.getTargetId()));
        }
        if (req.getUrl() != null) {
            n.setUrl(blankToNull(req.getUrl()));
        }
        if (req.getQueryParams() != null) {
            n.setQueryParams(req.getQueryParams().isEmpty() ? null : writeJson(req.getQueryParams()));
        }
        if (req.getIcon() != null) {
            n.setIcon(blankToNull(req.getIcon()));
        }
        if (req.getImage() != null) {
            n.setImage(blankToNull(req.getImage()));
        }
        if (req.getOpenInNewTab() != null) {
            n.setOpenInNewTab(req.getOpenInNewTab());
        }
        if (req.getShowOnPlp() != null) {
            n.setShowOnPlp(req.getShowOnPlp());
        }
        if (req.getShowOnMenuPopup() != null) {
            n.setShowOnMenuPopup(req.getShowOnMenuPopup());
        }
        if (depth >= MAX_DEPTH) {
            n.setShowOnMenuPopup(false); // cấp cuối không hiện trong menu popup
        }
        if (req.getSortOrder() != null) {
            if (req.getSortOrder() < 0) {
                throw invalid("sortOrder không được âm");
            }
            n.setSortOrder(req.getSortOrder());
        }
        validateTarget(n);
    }

    private void validateTarget(CmsNavigationEntity n) {
        switch (n.getType()) {
            case "CATEGORY" -> {
                Long id = numeric(n.getTargetId());
                var c = id == null ? null : categories.findOne(id);
                if (c == null || c.getDeletedDate() != null) {
                    throw BusinessException.badRequest("NAVIGATION_TARGET_INVALID",
                            "Danh mục không tồn tại hoặc đã bị xoá: " + n.getTargetId(), n.getTargetId());
                }
            }
            case "PAGE" -> {
                Long id = numeric(n.getTargetId());
                var p = id == null ? null : pages.findOne(id);
                if (p == null || p.getDeletedDate() != null) {
                    throw BusinessException.badRequest("NAVIGATION_TARGET_INVALID",
                            "Trang nội dung không tồn tại hoặc đã bị xoá: " + n.getTargetId(), n.getTargetId());
                }
            }
            case "LINK" -> {
                String u = n.getUrl() == null ? "" : n.getUrl();
                if (!(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("/"))) {
                    throw invalid("Với type LINK, url bắt buộc và phải bắt đầu bằng http://, https:// hoặc /");
                }
            }
            default -> { /* NONE: không ràng buộc */ }
        }
    }

    private void applyBadge(CmsNavigationEntity n, AdminNavigationRequestDto req, Long actorId) {
        if (Boolean.TRUE.equals(req.getClearBadge())) {
            for (CmsNavigationBadgeEntity b : badgesOf(n.getId())) {
                badges.delete(b);
            }
        }
        AdminNavigationRequestDto.Badge in = req.getBadge();
        if (in == null) {
            return;
        }
        String design = in.getDesignType() == null ? "" : in.getDesignType().trim().toUpperCase();
        if (!DESIGNS.contains(design)) {
            throw badgeInvalid("designType không hợp lệ (TEXT | IMAGE)");
        }
        String text = in.getDisplayText() == null ? "" : in.getDisplayText().trim();
        String image = in.getImageUrl() == null ? "" : in.getImageUrl().trim();
        if ("TEXT".equals(design) && (text.isEmpty() || text.length() > 5)) {
            throw badgeInvalid("Nhãn dạng TEXT cần displayText từ 1 đến 5 ký tự");
        }
        if ("IMAGE".equals(design) && image.isEmpty()) {
            throw badgeInvalid("Nhãn dạng IMAGE cần imageUrl");
        }
        Date start = parseDate(in.getStartTime(), "startTime");
        if (start == null) {
            throw badgeInvalid("startTime bắt buộc");
        }
        Date end = parseDate(in.getEndTime(), "endTime");
        if (end != null && !end.after(start)) {
            throw badgeInvalid("endTime phải sau startTime");
        }
        List<CmsNavigationBadgeEntity> mine = badgesOf(n.getId());
        CmsNavigationBadgeEntity target = null;
        if (in.getId() != null) {
            target = mine.stream().filter(b -> b.getId().equals(in.getId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Nhãn menu", "NavigationBadge", in.getId()));
        }
        final CmsNavigationBadgeEntity self = target;
        List<CmsNavigationBadgeEntity> conflicts = mine.stream().filter(b -> self == null || !b.getId().equals(self.getId()))
                .filter(b -> overlaps(start, end, b.getStartTime(), b.getEndTime())).toList();
        if (!conflicts.isEmpty()) {
            if (!Boolean.TRUE.equals(in.getConfirmReplaceSchedule())) {
                List<Map<String, Object>> deps = conflicts.stream().map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", String.valueOf(b.getId()));
                    m.put("designType", b.getDesignType());
                    m.put("displayText", b.getDisplayText());
                    m.put("startTime", iso(b.getStartTime()));
                    m.put("endTime", b.getEndTime() == null ? null : iso(b.getEndTime()));
                    return m;
                }).collect(Collectors.toList());
                throw BusinessException.conflict("NAVIGATION_BADGE_SCHEDULE_CONFLICT",
                        "Nhãn trùng lịch hiển thị với " + conflicts.size() + " nhãn khác của mục menu này. Gửi confirmReplaceSchedule=true để thay thế.")
                        .withDependencies(deps);
            }
            for (CmsNavigationBadgeEntity c : conflicts) {
                badges.delete(c);
            }
        }
        CmsNavigationBadgeEntity b = target != null ? target : new CmsNavigationBadgeEntity();
        boolean created = target == null;
        b.setLayer(CmsNavigationEntity.DRAFT);
        b.setNavigationId(n.getId());
        b.setDesignType(design);
        b.setDisplayText("TEXT".equals(design) ? text : null);
        b.setImageUrl("IMAGE".equals(design) ? image : null);
        b.setStyleConfig(in.getStyleConfig() == null || in.getStyleConfig().isEmpty() ? null : writeJson(in.getStyleConfig()));
        b.setStartTime(start);
        b.setEndTime(end);
        if (created) {
            b.setCreatedId(actorId);
            badges.create(b);
        } else {
            b.setUpdatedId(actorId);
            b.touch();
            badges.update(b);
        }
    }

    // ============================================================ helpers

    private static final Comparator<CmsNavigationEntity> NODE_ORDER =
            Comparator.comparingInt(CmsNavigationEntity::getSortOrder).thenComparing(CmsNavigationEntity::getId);

    private List<CmsNavigationEntity> layerNodes(String layer, boolean includeArchived) {
        return navs.findAll(Sort.unsorted()).stream()
                .filter(n -> layer.equals(n.getLayer()))
                .filter(n -> includeArchived || !n.isArchived())
                .sorted(NODE_ORDER).toList();
    }

    private Map<Long, List<CmsNavigationBadgeEntity>> badgesByNav(String layer) {
        return badges.findAll(Sort.unsorted()).stream().filter(b -> layer.equals(b.getLayer()))
                .sorted(Comparator.comparing(CmsNavigationBadgeEntity::getStartTime).thenComparing(CmsNavigationBadgeEntity::getId))
                .collect(Collectors.groupingBy(CmsNavigationBadgeEntity::getNavigationId, LinkedHashMap::new, Collectors.toList()));
    }

    private List<CmsNavigationBadgeEntity> badgesOf(Long navId) {
        return badgesByNav(CmsNavigationEntity.DRAFT).getOrDefault(navId, List.of());
    }

    private CmsNavigationEntity requireDraft(Long id) {
        CmsNavigationEntity n = id == null ? null : navs.findOne(id);
        if (n == null || !CmsNavigationEntity.DRAFT.equals(n.getLayer())) {
            throw new ResourceNotFoundException("Mục menu", "Navigation", id);
        }
        return n;
    }

    private static List<CmsNavigationEntity> childrenOf(Collection<CmsNavigationEntity> all, Long parentId) {
        return all.stream().filter(n -> java.util.Objects.equals(n.getParentId(), parentId)).sorted(NODE_ORDER).toList();
    }

    private static int depthOf(CmsNavigationEntity n, Collection<CmsNavigationEntity> all) {
        Map<Long, CmsNavigationEntity> byId = all.stream().collect(Collectors.toMap(CmsNavigationEntity::getId, x -> x, (a, b) -> a));
        int d = 1;
        CmsNavigationEntity cur = n;
        while (cur.getParentId() != null && byId.containsKey(cur.getParentId()) && d < 50) {
            cur = byId.get(cur.getParentId());
            d++;
        }
        return d;
    }

    private List<NavigationResponseDto> buildAdminTree(List<CmsNavigationEntity> all,
                                                       Map<Long, List<CmsNavigationBadgeEntity>> bs, Long parentId) {
        List<NavigationResponseDto> out = new ArrayList<>();
        for (CmsNavigationEntity n : childrenOf(all, parentId)) {
            out.add(toAdmin(n, bs, childrenOf(all, n.getId()), buildAdminTree(all, bs, n.getId())));
        }
        return out;
    }

    private NavigationResponseDto toAdmin(CmsNavigationEntity n, Map<Long, List<CmsNavigationBadgeEntity>> bs,
                                          List<CmsNavigationEntity> directChildren) {
        // detail: chỉ trả con trực tiếp (kèm badge) không đệ quy sâu
        List<NavigationResponseDto> kids = directChildren.stream()
                .map(c -> toAdmin(c, bs, List.of(), List.of())).toList();
        return toAdmin(n, bs, directChildren, kids);
    }

    private NavigationResponseDto toAdmin(CmsNavigationEntity n, Map<Long, List<CmsNavigationBadgeEntity>> bs,
                                          List<CmsNavigationEntity> unused, List<NavigationResponseDto> kids) {
        List<NavigationResponseDto.Badge> bl = bs.getOrDefault(n.getId(), List.of()).stream()
                .map(b -> new NavigationResponseDto.Badge(b.getId(), b.getNavigationId(), b.getDesignType(),
                        b.getDisplayText(), b.getImageUrl(), readMap(b.getStyleConfig()), iso(b.getStartTime()),
                        b.getEndTime() == null ? null : iso(b.getEndTime()), b.getLayer(), iso(b.getCreatedDate()),
                        b.getUpdatedDate() == null ? null : iso(b.getUpdatedDate()))).toList();
        return new NavigationResponseDto(n.getId(), TENANT, n.getParentId(), n.getLabel(), n.getType(), n.getTargetId(),
                n.getUrl(), readMap(n.getQueryParams()), n.getIcon(), n.getImage(), n.isOpenInNewTab(), n.getSortOrder(),
                n.isShowOnMenuPopup(), n.isShowOnPlp(), n.isArchived(), n.getLayer(), bl, kids, iso(n.getCreatedDate()),
                n.getUpdatedDate() == null ? null : iso(n.getUpdatedDate()));
    }

    private CmsNavigationPublishEntity publishRow() {
        return publishRepo.findAll(Sort.unsorted()).stream().min(Comparator.comparing(CmsNavigationPublishEntity::getId)).orElse(null);
    }

    private String draftFingerprint() {
        return fingerprint(layerNodes(CmsNavigationEntity.DRAFT, false),
                badgesByNav(CmsNavigationEntity.DRAFT).values().stream().flatMap(List::stream).toList());
    }

    private String fingerprint(List<CmsNavigationEntity> nodes, List<CmsNavigationBadgeEntity> bs) {
        if (nodes.isEmpty()) {
            return "";
        }
        java.util.Set<Long> live = nodes.stream().map(CmsNavigationEntity::getId).collect(Collectors.toSet());
        StringBuilder sb = new StringBuilder();
        nodes.stream().sorted(Comparator.comparing(CmsNavigationEntity::getId)).forEach(n -> sb.append("N|").append(n.getId())
                .append('|').append(n.getParentId()).append('|').append(n.getLabel()).append('|').append(n.getType())
                .append('|').append(n.getTargetId()).append('|').append(n.getUrl()).append('|').append(n.getQueryParams())
                .append('|').append(n.getIcon()).append('|').append(n.getImage()).append('|').append(n.isOpenInNewTab())
                .append('|').append(n.getSortOrder()).append('|').append(n.isShowOnMenuPopup()).append('|')
                .append(n.isShowOnPlp()).append('\n'));
        bs.stream().filter(b -> live.contains(b.getNavigationId())).sorted(Comparator.comparing(CmsNavigationBadgeEntity::getId))
                .forEach(b -> sb.append("B|").append(b.getId()).append('|').append(b.getNavigationId()).append('|')
                        .append(b.getDesignType()).append('|').append(b.getDisplayText()).append('|').append(b.getImageUrl())
                        .append('|').append(b.getStyleConfig()).append('|').append(b.getStartTime().getTime()).append('|')
                        .append(b.getEndTime() == null ? "" : b.getEndTime().getTime()).append('\n'));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte x : h) {
                hex.append(String.format("%02x", x));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean overlaps(Date aStart, Date aEnd, Date bStart, Date bEnd) {
        long ae = aEnd == null ? Long.MAX_VALUE : aEnd.getTime();
        long be = bEnd == null ? Long.MAX_VALUE : bEnd.getTime();
        return aStart.getTime() < be && bStart.getTime() < ae;
    }

    private static Date parseDate(String s, String field) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        try {
            return Date.from(Instant.parse(v));
        } catch (RuntimeException ignore) {
            // thử dạng khác
        }
        try {
            return Date.from(OffsetDateTime.parse(v).toInstant());
        } catch (RuntimeException ignore) {
            // thử dạng khác
        }
        try {
            return Date.from(LocalDateTime.parse(v).atZone(ZONE).toInstant());
        } catch (RuntimeException e) {
            throw badgeInvalid(field + " không phải thời gian ISO-8601: " + s);
        }
    }

    private static String iso(Date d) {
        return d == null ? null : d.toInstant().toString();
    }

    private static Long numeric(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : (b != null && !b.isBlank() ? b.trim() : "");
    }

    private static BusinessException invalid(String message) {
        return BusinessException.badRequest("NAVIGATION_INVALID", message);
    }

    private static BusinessException badgeInvalid(String message) {
        return BusinessException.badRequest("NAVIGATION_BADGE_INVALID", message);
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw invalid("JSON không hợp lệ");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

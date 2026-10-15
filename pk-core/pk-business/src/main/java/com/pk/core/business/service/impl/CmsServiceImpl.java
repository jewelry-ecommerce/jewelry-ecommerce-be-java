package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.CmsBlockRepo;
import com.pk.core.business.repository.CmsPageRepo;
import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.PromotionRepo;
import com.pk.core.business.service.BannerService;
import com.pk.core.business.service.CmsService;
import com.pk.core.business.service.ProductService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.SlugUtil;
import com.pk.core.model.dto.request.AdminCmsBlockRequestDto;
import com.pk.core.model.dto.request.AdminCmsPageRequestDto;
import com.pk.core.model.dto.response.BannerPlacementRenderResponseDto;
import com.pk.core.model.dto.response.CmsBlockResponseDto;
import com.pk.core.model.dto.response.CmsPageResponseDto;
import com.pk.core.model.dto.response.CmsStorefrontBlockResponseDto;
import com.pk.core.model.dto.response.CmsStorefrontPageResponseDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.entity.CmsBlockEntity;
import com.pk.core.model.entity.CollectionEntity;
import com.pk.core.model.entity.CmsPageEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.PromotionEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CmsServiceImpl implements CmsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> BANNER_LAYOUTS = Set.of("SLIDER", "GRID", "DOUBLE");
    private static final Set<String> PAGE_STATUSES = Set.of(CmsPageEntity.DRAFT, CmsPageEntity.PUBLISHED);
    private static final List<String> CAROUSEL_FILTER_TYPES = List.of("MANUAL_PRODUCTS", "CATEGORY", "COLLECTION",
            "CAMPAIGN", "NEW_ARRIVALS", "BEST_SELLERS", "MOST_FAVORITED");
    private static final Set<String> DEVICES = Set.of("ALL", "DESKTOP", "MOBILE");
    private static final int DEFAULT_CAROUSEL_LIMIT = 12;
    private static final int MAX_CAROUSEL_LIMIT = 50;

    private final CmsPageRepo pages;
    private final CmsBlockRepo blocks;
    private final ObjectMapper objectMapper;
    private final BannerService bannerService;
    private final ProductService productService;
    private final ProductRepo products;
    private final CollectionRepo collections;
    private final PromotionRepo promotions;

    // ============================== Admin ==============================

    @Transactional(readOnly = true)
    @Override
    public List<CmsPageResponseDto> findAllPages() {
        // findAll() KHÔNG tự lọc soft-delete (Mirage/PkRepo không biết deleted_date) - lọc tay bằng Java.
        return pages.findAll(Sort.unsorted()).stream()
                .filter(p -> p.getDeletedDate() == null)
                .map(p -> pageDto(p, toBlockDtos(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CmsPageResponseDto findPageById(Long pageId) {
        CmsPageEntity page = findPageOrThrow(pageId);
        return pageDto(page, toBlockDtos(page.getId()));
    }

    @Transactional
    @Override
    public CmsPageResponseDto createPage(AdminCmsPageRequestDto req) {
        String slug = resolveSlug(req.getSlug(), req.getName());
        checkSlugFree(slug, null);
        // Kiểm tra block TRƯỚC khi ghi để lỗi cấu trúc không để lại trang dở dang.
        List<CmsBlockEntity> newBlocks = buildBlocks(null, req.getBlocks());
        boolean publishNow = wantsPublish(req);
        CmsPageEntity page = new CmsPageEntity(slug, req.getName().trim(), CmsPageEntity.DRAFT);
        applyPageExtras(page, req);
        pages.create(page);
        for (CmsBlockEntity b : newBlocks) {
            b.setPageId(page.getId());
            blocks.create(b);
        }
        if (publishNow) {
            capture(page);
        }
        return pageDto(page, toBlockDtos(page.getId()));
    }

    /** Lưu BẢN NHÁP (metadata + blocks nếu có). Không đổi bản public, trừ khi request.status = PUBLISHED (xuất bản ngay). */
    @Transactional
    @Override
    public CmsPageResponseDto updatePage(Long pageId, AdminCmsPageRequestDto req) {
        CmsPageEntity page = findPageOrThrow(pageId);
        ensureSnapshot(page);

        String slug = resolveSlug(req.getSlug(), req.getName());
        checkSlugFree(slug, pageId);
        List<CmsBlockEntity> newBlocks = req.getBlocks() == null ? null : buildBlocks(pageId, req.getBlocks());
        boolean publishNow = wantsPublish(req);

        page.setSlug(slug);
        page.setTitle(req.getName().trim());
        applyPageExtras(page, req);
        page.touch();
        pages.update(page);

        // blocks == null -> giữ nguyên blocks hiện có. blocks != null (kể cả []) -> THAY TOÀN BỘ (xoá hết rồi tạo lại).
        if (newBlocks != null) {
            for (CmsBlockEntity old : blocks.findByPageId(pageId)) {
                blocks.delete(old);
            }
            for (CmsBlockEntity b : newBlocks) {
                blocks.create(b);
            }
        }
        if (publishNow) {
            capture(page);
        }
        return pageDto(page, toBlockDtos(pageId));
    }

    /** Xuất bản: chụp bản nháp hiện tại thành bản public mới (tăng versionId). */
    @Transactional
    @Override
    public CmsPageResponseDto publishPage(Long pageId) {
        CmsPageEntity page = findPageOrThrow(pageId);
        capture(page);
        return pageDto(page, toBlockDtos(pageId));
    }

    /** Gỡ bản public (về DRAFT); bản nháp giữ nguyên để xuất bản lại sau. */
    @Transactional
    @Override
    public CmsPageResponseDto unpublishPage(Long pageId) {
        CmsPageEntity page = findPageOrThrow(pageId);
        page.setStatus(CmsPageEntity.DRAFT);
        page.setPublishedSnapshot(null);
        page.setPublishedSlug(null);
        page.setPublishedHash(null);
        page.setPublishedAt(null);
        page.touch();
        pages.update(page);
        return pageDto(page, toBlockDtos(pageId));
    }

    /** Xoá MỀM trang: ẩn khỏi admin và storefront, nhả slug để tạo trang khác dùng lại. Không đụng banner/sản phẩm được block tham chiếu. */
    @Transactional
    @Override
    public void deletePage(Long pageId) {
        CmsPageEntity page = findPageOrThrow(pageId);
        String suffix = "-deleted-" + page.getId();
        String base = page.getSlug();
        page.setSlug((base.length() + suffix.length() > 140 ? base.substring(0, 140 - suffix.length()) : base) + suffix);
        page.setStatus(CmsPageEntity.DRAFT);
        page.setPublishedSnapshot(null);
        page.setPublishedSlug(null);
        page.setPublishedHash(null);
        page.setPublishedAt(null);
        page.setDeletedDate(new Date());
        pages.update(page);
    }

    @Transactional
    @Override
    public CmsBlockResponseDto addBlock(Long pageId, AdminCmsBlockRequestDto req) {
        ensureSnapshot(findPageOrThrow(pageId));
        CmsBlockEntity block = buildBlock(pageId, req);
        blocks.create(block);
        return toBlockDto(block);
    }

    @Transactional
    @Override
    public CmsBlockResponseDto updateBlock(Long pageId, Long blockId, AdminCmsBlockRequestDto req) {
        ensureSnapshot(findPageOrThrow(pageId));
        CmsBlockEntity block = findBlockOrThrow(pageId, blockId);
        CmsBlockEntity fresh = buildBlock(pageId, req);
        block.setType(fresh.getType());
        block.setSortOrder(fresh.getSortOrder());
        block.setData(fresh.getData());
        block.setTargetSegment(fresh.getTargetSegment());
        if (req.getVisible() != null) {
            block.setVisible(req.getVisible());
        }
        block.touch();
        blocks.update(block);
        return toBlockDto(block);
    }

    @Transactional
    @Override
    public void deleteBlock(Long pageId, Long blockId) {
        ensureSnapshot(findPageOrThrow(pageId));
        CmsBlockEntity block = findBlockOrThrow(pageId, blockId);
        blocks.delete(block);
    }

    // ============================== Storefront ==============================

    /** Phục vụ BẢN PUBLIC (snapshot lúc xuất bản), không phải bản nháp. 404 nếu chưa xuất bản/đã gỡ/đã xoá, trang bị tắt,
     * ngoài lịch [scheduleStart, scheduleEnd) hoặc `device` (DESKTOP|MOBILE, tuỳ chọn) không khớp targetDevice. */
    @Transactional(readOnly = true)
    @Override
    public CmsStorefrontPageResponseDto findPublishedBySlug(String slug, String device) {
        String key = (slug == null || slug.isBlank()) ? null : slug.trim().toLowerCase();
        CmsPageEntity page = key == null ? null : pages.findByPublishedSlug(key);
        boolean legacy = false;
        if (page == null && key != null) {
            // Trang PUBLISHED từ trước khi có snapshot (chưa ai sửa từ sau V22): phục vụ trực tiếp như cũ.
            CmsPageEntity candidate = pages.findBySlug(key);
            if (candidate != null && CmsPageEntity.PUBLISHED.equals(candidate.getStatus())
                    && candidate.getPublishedSnapshot() == null) {
                page = candidate;
                legacy = true;
            }
        }
        if (page == null || page.getDeletedDate() != null || !page.isActive()
                || !CmsPageEntity.PUBLISHED.equals(page.getStatus())) {
            throw new ResourceNotFoundException("Trang CMS", "CmsPage", slug);
        }
        Map<String, Object> snap = legacy ? content(page, toBlockDtos(page.getId()), true)
                : readMap(page.getPublishedSnapshot());

        String target = text(snap.get("targetDevice"));
        target = target == null ? "ALL" : target.toUpperCase(Locale.ROOT);
        String dev = text(device) == null ? null : text(device).toUpperCase(Locale.ROOT);
        if (dev != null && DEVICES.contains(dev) && !"ALL".equals(dev) && !"ALL".equals(target) && !target.equals(dev)) {
            throw new ResourceNotFoundException("Trang CMS", "CmsPage", slug);
        }
        Date now = new Date();
        Date start = parseDate(snap.get("scheduleStart"));
        Date end = parseDate(snap.get("scheduleEnd"));
        if ((start != null && now.before(start)) || (end != null && !now.before(end))) {
            throw new ResourceNotFoundException("Trang CMS", "CmsPage", slug);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        if (snap.get("blocks") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) m;
                    if (!Boolean.FALSE.equals(row.get("visible"))) {
                        rows.add(row);
                    }
                }
            }
        }
        rows.sort(java.util.Comparator.comparingInt(r -> r.get("sortOrder") instanceof Number n ? n.intValue() : 0));
        List<CmsStorefrontBlockResponseDto> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> config = r.get("config") instanceof Map<?, ?> c ? castMap(c) : new LinkedHashMap<>();
            String type = text(r.get("type"));
            int sort = r.get("sortOrder") instanceof Number n ? n.intValue() : 0;
            out.add(new CmsStorefrontBlockResponseDto(text(r.get("id")), type, sort, config, resolveContent(type, config)));
        }
        Map<String, Object> seo = snap.get("seo") instanceof Map<?, ?> sm ? castMap(sm) : null;
        String name = text(snap.get("name"));
        String locale = text(snap.get("locale"));
        return new CmsStorefrontPageResponseDto(String.valueOf(page.getId()), name == null ? page.getTitle() : name,
                text(snap.get("slug")) == null ? page.getSlug() : text(snap.get("slug")), CmsPageEntity.PUBLISHED, out,
                locale == null ? page.getLocale() : locale, seo, target, Math.max(1, page.getPublishedVersion()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    /** Nạp sẵn dữ liệu cho các loại block có nội dung động; loại khác trả null (client tự dùng `config`). */
    private Map<String, Object> resolveContent(String type, Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        if (CmsBlockEntity.BANNER.equals(type)) {
            return resolveBanner(config);
        }
        if (CmsBlockEntity.PRODUCT_CAROUSEL.equals(type)) {
            return resolveCarousel(config);
        }
        return null;
    }

    private Map<String, Object> resolveBanner(Map<String, Object> config) {
        Map<String, Object> content = new LinkedHashMap<>();
        String code = text(config.get("placementCode"));
        BannerPlacementRenderResponseDto render = null;
        if (code != null) {
            try {
                render = bannerService.renderByPlacementCode(code);
            } catch (ResourceNotFoundException ex) {
                render = null; // vị trí đã bị xoá/không tồn tại: trang vẫn hiển thị, block không có banner
            }
        }
        String layout = text(config.get("layout"));
        layout = layout == null ? null : layout.toUpperCase(Locale.ROOT);
        if (layout == null && render != null) {
            layout = layoutFromDisplayType(render.getDisplayType());
        }
        content.put("layout", layout);
        if (render != null) {
            Map<String, Object> placement = new LinkedHashMap<>();
            placement.put("code", render.getCode());
            placement.put("name", render.getName());
            placement.put("displayType", render.getDisplayType());
            content.put("placement", placement);
            content.put("banners", render.getBanners());
        } else {
            content.put("placement", null);
            content.put("banners", List.of());
        }
        return content;
    }

    private static String layoutFromDisplayType(String displayType) {
        if (displayType == null) {
            return null;
        }
        return switch (displayType.toUpperCase(Locale.ROOT)) {
            case "CAROUSEL" -> "SLIDER";
            case "GRID" -> "GRID";
            default -> displayType.toUpperCase(Locale.ROOT);
        };
    }

    /** Admin FE lưu nguồn dữ liệu carousel ở config.dataSource.{filterType,...}; khóa legacy cấp trên cùng vẫn được đọc. */
    private static Object carouselValue(Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v == null && config.get("dataSource") instanceof Map<?, ?> ds) {
            v = ds.get(key);
        }
        return v;
    }

    private static String carouselFilterType(Map<String, Object> config) {
        String t = text(carouselValue(config, "filterType"));
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    /** categorySlugs[] (+ categoryId, FE dùng làm slug đầu tiên), không trùng, giữ thứ tự. */
    private static List<String> carouselCategorySlugs(Map<String, Object> config) {
        java.util.LinkedHashSet<String> slugs = new java.util.LinkedHashSet<>();
        if (carouselValue(config, "categorySlugs") instanceof List<?> items) {
            for (Object item : items) {
                String t = text(item);
                if (t != null) {
                    slugs.add(t);
                }
            }
        }
        String legacy = text(carouselValue(config, "categoryId"));
        if (legacy != null) {
            slugs.add(legacy);
        }
        return new ArrayList<>(slugs);
    }

    /** collectionSlug, hoặc collectionId (id số -> tra slug; không phải số thì coi là slug). */
    private String carouselCollectionSlug(Map<String, Object> config) {
        String slug = text(carouselValue(config, "collectionSlug"));
        if (slug != null) {
            return slug;
        }
        String id = text(carouselValue(config, "collectionId"));
        if (id == null) {
            return null;
        }
        try {
            CollectionEntity c = collections.findOne(Long.valueOf(id));
            return c == null ? null : c.getSlug();
        } catch (NumberFormatException ex) {
            return id;
        }
    }

    private Map<String, Object> resolveCarousel(Map<String, Object> config) {
        List<ProductResponseDto> list = new ArrayList<>();
        int limit = carouselLimit(config);
        String filterType = carouselFilterType(config);
        Object ids = carouselValue(config, "productIds");
        if (filterType == null || "MANUAL_PRODUCTS".equals(filterType)) {
            // Legacy (không có filterType): productIds hoặc collectionSlug.
            if (ids instanceof List<?> items && !items.isEmpty()) {
                for (Object item : items) {
                    ProductResponseDto p = findProduct(item);
                    if (p != null) {
                        list.add(p);
                    }
                }
            } else if (filterType == null && carouselCollectionSlug(config) != null) {
                list.addAll(productService.search(null, carouselCollectionSlug(config), null, null, null, null,
                        "NEWEST", 1, limit).content());
            }
        } else if ("COLLECTION".equals(filterType)) {
            String slug = carouselCollectionSlug(config);
            if (slug != null) {
                list.addAll(productService.search(null, slug, null, null, null, null, "NEWEST", 1, limit).content());
            }
        } else if ("CATEGORY".equals(filterType)) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (String slug : carouselCategorySlugs(config)) {
                for (ProductResponseDto p : productService.search(slug, null, null, null, null, null, "NEWEST", 1, limit).content()) {
                    if (seen.add(p.getId()) && list.size() < limit) {
                        list.add(p);
                    }
                }
            }
        } else if ("NEW_ARRIVALS".equals(filterType)) {
            list.addAll(productService.search(null, null, null, null, null, null, "NEWEST", 1, limit).content());
        } else if ("BEST_SELLERS".equals(filterType)) {
            fillFromEntities(list, products.findPublishedBySold(), limit);
        } else if ("MOST_FAVORITED".equals(filterType)) {
            fillFromEntities(list, products.findPublishedByFavorites(), limit);
        } else if ("CAMPAIGN".equals(filterType)) {
            for (Long id : campaignProductIds(config)) {
                if (list.size() >= limit) {
                    break;
                }
                ProductResponseDto p = findProduct(id);
                if (p != null) {
                    list.add(p);
                }
            }
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("header", config.get("header"));
        content.put("products", list);
        return content;
    }

    /** Duyệt danh sách đã xếp hạng, bỏ sản phẩm không hiển thị được (danh mục tắt...), dừng khi đủ `limit`. */
    private void fillFromEntities(List<ProductResponseDto> list, List<ProductEntity> ranked, int limit) {
        int tried = 0;
        for (ProductEntity e : ranked) {
            if (list.size() >= limit || tried++ >= limit * 5) {
                break;
            }
            if (e.getSlug() == null) {
                continue;
            }
            try {
                list.add(productService.findBySlug(e.getSlug()));
            } catch (ResourceNotFoundException ex) {
                // sản phẩm không còn hiển thị được: bỏ qua
            }
        }
    }

    /** Alias của FE: promotionId hoặc campaignId (cấp trên cùng hoặc trong config.dataSource); promotionId được ưu tiên. */
    private static String campaignReference(Map<String, Object> config) {
        String raw = text(carouselValue(config, "promotionId"));
        return raw != null ? raw : text(carouselValue(config, "campaignId"));
    }

    /** Cả 2 alias nếu có đều phải là id số; id phải trỏ tới 1 chương trình khuyến mãi tồn tại (bỏ trống = mọi khuyến mãi đang chạy). */
    private void validateCampaignReference(Map<String, Object> config) {
        for (String key : List.of("promotionId", "campaignId")) {
            if (!isBlankOrNumber(carouselValue(config, key))) {
                throw BusinessException.badRequest(ErrorCode.CAROUSEL_SOURCE_INVALID,
                        "dataSource." + key + " phải là id số của chương trình khuyến mãi", key);
            }
        }
        String raw = campaignReference(config);
        if (raw != null && promotions.findOne(Long.valueOf(raw)) == null) {
            throw BusinessException.badRequest(ErrorCode.CAROUSEL_SOURCE_INVALID,
                    "Chương trình khuyến mãi không tồn tại: " + raw, raw);
        }
    }

    /** CAMPAIGN: dataSource.promotionId (hoặc campaignId) chọn 1 chương trình đang hiệu lực; bỏ trống = mọi khuyến mãi đang chạy. */
    private List<Long> campaignProductIds(Map<String, Object> config) {
        String raw = campaignReference(config);
        Date now = new Date();
        if (raw == null) {
            List<Long> ids = promotions.findActiveProductIds(now);
            return ids == null ? List.of() : ids;
        }
        try {
            Long promotionId = Long.valueOf(raw);
            PromotionEntity promotion = promotions.findOne(promotionId);
            if (promotion == null || !promotion.isActiveAt(now)) {
                return List.of();
            }
            List<Long> ids = promotions.findProductIdsByPromotionId(promotionId);
            return ids == null ? List.of() : ids;
        } catch (NumberFormatException ex) {
            return List.of();
        }
    }

    /** `item` là id số hoặc slug sản phẩm; chỉ trả sản phẩm PUBLISHED (không có -> bỏ qua, không lỗi cả trang). */
    private ProductResponseDto findProduct(Object item) {
        String raw = text(item);
        if (raw == null) {
            return null;
        }
        String slug = raw;
        try {
            ProductEntity entity = products.findOne(Long.valueOf(raw));
            slug = entity == null ? null : entity.getSlug();
        } catch (NumberFormatException ex) {
            slug = raw;
        }
        if (slug == null) {
            return null;
        }
        try {
            return productService.findBySlug(slug);
        } catch (ResourceNotFoundException ex) {
            return null;
        }
    }

    /** FE gửi display.limit; khóa legacy config.limit vẫn được đọc. */
    private static int carouselLimit(Map<String, Object> config) {
        Object limit = config.get("limit");
        if (!(limit instanceof Number) && config.get("display") instanceof Map<?, ?> d) {
            limit = d.get("limit");
        }
        int value = limit instanceof Number n ? n.intValue() : DEFAULT_CAROUSEL_LIMIT;
        return Math.max(1, Math.min(MAX_CAROUSEL_LIMIT, value));
    }

    private void validateCarouselConfig(Map<String, Object> config) {
        Object ids = carouselValue(config, "productIds");
        if (ids != null && !(ids instanceof List<?>)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "config.dataSource.productIds phải là mảng");
        }
        boolean hasIds = ids instanceof List<?> l && !l.isEmpty();
        String filterType = carouselFilterType(config);
        if (filterType == null) {
            if (!hasIds && text(carouselValue(config, "collectionSlug")) == null) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Block PRODUCT_CAROUSEL cần config.dataSource.filterType, hoặc config.productIds / config.collectionSlug");
            }
        } else if (!CAROUSEL_FILTER_TYPES.contains(filterType)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "dataSource.filterType không hợp lệ: " + filterType + " ("
                            + String.join(" | ", CAROUSEL_FILTER_TYPES) + ")", filterType);
        } else if ("MANUAL_PRODUCTS".equals(filterType) && !hasIds) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Block PRODUCT_CAROUSEL (MANUAL_PRODUCTS) cần dataSource.productIds có ít nhất 1 sản phẩm");
        } else if ("CAMPAIGN".equals(filterType)) {
            validateCampaignReference(config);
        } else if ("CATEGORY".equals(filterType) && carouselCategorySlugs(config).isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Block PRODUCT_CAROUSEL (CATEGORY) cần dataSource.categorySlugs hoặc dataSource.categoryId");
        } else if ("COLLECTION".equals(filterType)
                && text(carouselValue(config, "collectionSlug")) == null
                && text(carouselValue(config, "collectionId")) == null) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Block PRODUCT_CAROUSEL (COLLECTION) cần dataSource.collectionSlug hoặc dataSource.collectionId");
        }
    }

    private static boolean isBlankOrNumber(Object value) {
        String t = text(value);
        if (t == null) {
            return true;
        }
        try {
            Long.parseLong(t);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    // ============================== helpers ==============================

    /** status = PUBLISHED trong request nghĩa là "lưu rồi xuất bản ngay" (tương thích client cũ). */
    private static boolean wantsPublish(AdminCmsPageRequestDto req) {
        return req.getStatus() != null && !req.getStatus().isBlank()
                && CmsPageEntity.PUBLISHED.equals(normalizeStatus(req.getStatus()));
    }

    /** Slug (của nháp hoặc của bản public) chưa bị trang khác dùng. */
    private void checkSlugFree(String slug, Long selfId) {
        CmsPageEntity a = pages.findBySlug(slug);
        CmsPageEntity b = pages.findByPublishedSlug(slug);
        if ((a != null && !a.getId().equals(selfId)) || (b != null && !b.getId().equals(selfId))) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_SLUG, "Đường dẫn (slug) đã tồn tại: " + slug, slug);
        }
    }

    /** Trang PUBLISHED có từ trước V22 chưa có snapshot: chụp trạng thái hiện tại trước khi sửa để bản public không bị đổi theo nháp. */
    private void ensureSnapshot(CmsPageEntity page) {
        if (CmsPageEntity.PUBLISHED.equals(page.getStatus()) && page.getPublishedSnapshot() == null) {
            capture(page);
        }
    }

    /** Chụp bản nháp hiện tại thành bản public (snapshot + slug + hash), tăng versionId. */
    private void capture(CmsPageEntity page) {
        validateSchedule(page.getScheduleStart(), page.getScheduleEnd());
        checkSlugFree(page.getSlug(), page.getId());
        List<CmsBlockResponseDto> bs = toBlockDtos(page.getId());
        page.setPublishedSnapshot(toJson(content(page, bs, true)));
        page.setPublishedHash(sha256(toJson(content(page, bs, false))));
        page.setPublishedSlug(page.getSlug());
        page.setPublishedVersion(page.getPublishedVersion() + 1);
        page.setPublishedAt(new Date());
        page.setStatus(CmsPageEntity.PUBLISHED);
        page.touch();
        pages.update(page);
    }

    /** Nội dung xuất bản của nháp (thứ tự khoá cố định để băm ổn định). `withIds` = false dùng để so sánh thay đổi. */
    private Map<String, Object> content(CmsPageEntity page, List<CmsBlockResponseDto> bs, boolean withIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", page.getTitle());
        m.put("slug", page.getSlug());
        m.put("locale", page.getLocale());
        m.put("seo", readSeo(page));
        m.put("targetDevice", page.getTargetDevice() == null ? "ALL" : page.getTargetDevice());
        m.put("scheduleStart", iso(page.getScheduleStart()));
        m.put("scheduleEnd", iso(page.getScheduleEnd()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CmsBlockResponseDto b : bs) {
            Map<String, Object> r = new LinkedHashMap<>();
            if (withIds) {
                r.put("id", b.getId());
            }
            r.put("type", b.getType());
            r.put("sortOrder", b.getSortOrder());
            r.put("visible", b.isVisible());
            r.put("targetSegment", b.getTargetSegment());
            r.put("config", b.getConfig());
            rows.add(r);
        }
        m.put("blocks", rows);
        return m;
    }

    private static String iso(Date d) {
        return d == null ? null : Instant.ofEpochMilli(d.getTime()).toString();
    }

    private static Date parseDate(Object value) {
        String t = text(value);
        if (t == null) {
            return null;
        }
        try {
            return Date.from(Instant.parse(t));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void validateSchedule(Date start, Date end) {
        if (start != null && end != null && !end.after(start)) {
            throw BusinessException.badRequest(ErrorCode.CMS_SCHEDULE_INVALID,
                    "scheduleEnd phải sau scheduleStart", iso(start), iso(end));
        }
    }

    /** Trạng thái hiển thị theo thời điểm: DRAFT | PAUSED | SCHEDULED | RUNNING | ENDED (dựa vào lịch của bản public). */
    static String displayStatus(CmsPageEntity page, Map<String, Object> live, Date now) {
        if (!CmsPageEntity.PUBLISHED.equals(page.getStatus())) {
            return "DRAFT";
        }
        if (!page.isActive()) {
            return "PAUSED";
        }
        Date start = live == null ? null : parseDate(live.get("scheduleStart"));
        Date end = live == null ? null : parseDate(live.get("scheduleEnd"));
        if (start != null && now.before(start)) {
            return "SCHEDULED";
        }
        if (end != null && !now.before(end)) {
            return "ENDED";
        }
        return "RUNNING";
    }

    /** Thông tin bản đang public (null nếu chưa xuất bản). */
    private Map<String, Object> liveMeta(CmsPageEntity page) {
        if (!CmsPageEntity.PUBLISHED.equals(page.getStatus())) {
            return null;
        }
        Map<String, Object> snap = page.getPublishedSnapshot() == null ? null : readMap(page.getPublishedSnapshot());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versionId", Math.max(1, page.getPublishedVersion()));
        m.put("publishedAt", iso(page.getPublishedAt()));
        m.put("slug", snap == null ? page.getSlug() : snap.get("slug"));
        m.put("targetDevice", snap == null ? page.getTargetDevice() : snap.get("targetDevice"));
        m.put("scheduleStart", snap == null ? iso(page.getScheduleStart()) : snap.get("scheduleStart"));
        m.put("scheduleEnd", snap == null ? iso(page.getScheduleEnd()) : snap.get("scheduleEnd"));
        return m;
    }

    private List<CmsBlockEntity> buildBlocks(Long pageId, List<AdminCmsBlockRequestDto> reqBlocks) {
        List<CmsBlockEntity> list = new ArrayList<>();
        if (reqBlocks != null) {
            for (AdminCmsBlockRequestDto b : reqBlocks) {
                list.add(buildBlock(pageId, b));
            }
        }
        return list;
    }

    /** Chuẩn hoá + kiểm tra block (type thuộc spec, config đúng cấu trúc tối thiểu theo loại). */
    private CmsBlockEntity buildBlock(Long pageId, AdminCmsBlockRequestDto req) {
        String type = req.getType() == null ? "" : req.getType().trim().toUpperCase(Locale.ROOT);
        if (!CmsBlockEntity.TYPES.contains(type)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Loại block không hợp lệ: " + req.getType() + " (cho phép: " + new java.util.TreeSet<>(CmsBlockEntity.TYPES) + ")",
                    req.getType());
        }
        validateConfig(type, req.getConfig());
        String segment = req.getTargetSegment() == null || req.getTargetSegment().isBlank()
                ? null : req.getTargetSegment().trim();
        CmsBlockEntity block = new CmsBlockEntity(pageId, type, req.getSortOrder(), toJson(req.getConfig()), segment);
        block.setVisible(req.getVisible() == null || req.getVisible());
        return block;
    }

    private void validateConfig(String type, Map<String, Object> config) {
        if (config == null) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "config không được để trống");
        }
        if (CmsBlockEntity.BANNER.equals(type)) {
            if (text(config.get("placementCode")) == null) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Block BANNER cần config.placementCode (mã vị trí banner)");
            }
            String layout = text(config.get("layout"));
            if (layout != null && !BANNER_LAYOUTS.contains(layout.toUpperCase(Locale.ROOT))) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "config.layout không hợp lệ: " + layout + " (SLIDER | GRID | DOUBLE)", layout);
            }
        } else if (CmsBlockEntity.PRODUCT_CAROUSEL.equals(type)) {
            validateCarouselConfig(config);
        }
        // INFO_CARDS, IMAGE_GALLERY và các loại còn lại: chỉ cần config là object (cấu trúc do client quy ước).
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return CmsPageEntity.DRAFT;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!PAGE_STATUSES.contains(s)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "status không hợp lệ: " + status + " (DRAFT | PUBLISHED)", status);
        }
        return s;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private CmsPageResponseDto pageDto(CmsPageEntity page, List<CmsBlockResponseDto> blockDtos) {
        CmsPageResponseDto dto = CmsPageResponseDto.from(page, blockDtos, readSeo(page));
        Map<String, Object> live = liveMeta(page);
        dto.setLive(live);
        dto.setDisplayStatus(displayStatus(page, live, new Date()));
        dto.setHasUnpublishedChanges(CmsPageEntity.PUBLISHED.equals(page.getStatus())
                && page.getPublishedHash() != null
                && !page.getPublishedHash().equals(sha256(toJson(content(page, blockDtos, false)))));
        return dto;
    }

    /** SEO lưu dạng JSON text; chưa có thì null (storefront tự dùng tiêu đề trang làm title mặc định). */
    private Map<String, Object> readSeo(CmsPageEntity page) {
        return page.getSeo() == null || page.getSeo().isBlank() ? null : readMap(page.getSeo());
    }

    /** Áp locale / isActive / seo từ request (trường bỏ trống thì giữ nguyên giá trị hiện có). */
    private void applyPageExtras(CmsPageEntity page, AdminCmsPageRequestDto req) {
        if (req.getLocale() != null && !req.getLocale().isBlank()) {
            page.setLocale(req.getLocale().trim());
        }
        if (req.getActive() != null) {
            page.setActive(req.getActive());
        }
        if (req.getSeo() != null) {
            page.setSeo(toJson(req.getSeo()));
        }
        if (req.getTargetDevice() != null && !req.getTargetDevice().isBlank()) {
            String device = req.getTargetDevice().trim().toUpperCase(Locale.ROOT);
            if (!DEVICES.contains(device)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "targetDevice không hợp lệ: " + req.getTargetDevice() + " (ALL | DESKTOP | MOBILE)", req.getTargetDevice());
            }
            page.setTargetDevice(device);
        }
        if (req.isScheduleStartProvided()) {
            page.setScheduleStart(req.getScheduleStart());
        }
        if (req.isScheduleEndProvided()) {
            page.setScheduleEnd(req.getScheduleEnd());
        }
        validateSchedule(page.getScheduleStart(), page.getScheduleEnd());
    }

    private List<CmsBlockResponseDto> toBlockDtos(Long pageId) {
        return blocks.findByPageId(pageId).stream().map(this::toBlockDto).toList();
    }

    private CmsBlockResponseDto toBlockDto(CmsBlockEntity e) {
        CmsBlockResponseDto dto = new CmsBlockResponseDto(String.valueOf(e.getId()), e.getType(), e.getSortOrder(),
                readMap(e.getData()), e.getTargetSegment(), e.isVisible());
        dto.copyAudit(e);
        return dto;
    }

    private String toJson(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "config không phải JSON hợp lệ");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            // Dữ liệu cũ (chuỗi thô không phải JSON object): bọc lại để không làm hỏng cả trang.
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("raw", json);
            return raw;
        }
    }

    private CmsPageEntity findPageOrThrow(Long pageId) {
        CmsPageEntity page = pages.findOne(pageId);
        if (page == null || page.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Trang CMS", "CmsPage", pageId);
        }
        return page;
    }

    /** IDOR-kiểu: đảm bảo blockId thực sự thuộc pageId trên URL. */
    private CmsBlockEntity findBlockOrThrow(Long pageId, Long blockId) {
        CmsBlockEntity block = blocks.findOne(blockId);
        if (block == null || !block.getPageId().equals(pageId)) {
            throw new ResourceNotFoundException("Block CMS", "CmsBlock", blockId);
        }
        return block;
    }

    private static String resolveSlug(String slug, String name) {
        return (slug == null || slug.isBlank()) ? SlugUtil.slugify(name) : SlugUtil.slugify(slug);
    }
}

package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.ProductAttributeRepo;
import com.pk.core.business.repository.ProductInfoRepo;
import com.pk.core.business.repository.ProductMediaRepo;
import com.pk.core.business.repository.ProductMixMatchRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.SkuAttributeValueRepo;
import com.pk.core.business.service.ProductService;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.SlugUtil;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminCreateProductRequestDto;
import com.pk.core.model.dto.request.AdminProductExtraRequestDto;
import com.pk.core.model.dto.request.AdminSkuAttributesRequestDto;
import com.pk.core.model.dto.request.AdminSkuRequestDto;
import com.pk.core.model.dto.request.AdminUpdateProductRequestDto;
import com.pk.core.model.dto.response.CategoryResponseDto;
import com.pk.core.model.dto.response.CollectionResponseDto;
import com.pk.core.model.dto.response.ProductInfoResponseDto;
import com.pk.core.model.dto.response.ProductMediaResponseDto;
import com.pk.core.model.dto.response.ProductMixMatchResponseDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.dto.response.ProductSkuResponseDto;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductInfoEntity;
import com.pk.core.model.entity.ProductMixMatchEntity;
import com.pk.core.model.entity.ProductAttributeEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.SkuAttributeValueEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Không lọc = truyền chuỗi rỗng/khoảng giá [0, MAX] xuống ProductRepo (xem lý do ở
 * ProductRepo_searchPublished.sql - tránh cú pháp SQL điều kiện động chưa có tiền lệ trong dự án).
 * Sort và phân trang làm ở tầng Java (không ORDER BY/LIMIT động trong SQL, xem ProductRepo). */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final BigDecimal NO_MAX_PRICE = new BigDecimal("999999999999");

    private final ProductRepo products;
    private final ProductSkuRepo productSkus;
    private final CategoryRepo categories;
    private final CollectionRepo collections;
    private final PromotionPricingService promotionPricing;
    private final ProductAttributeRepo attributeDefs;
    private final SkuAttributeValueRepo skuAttributes;
    private final ProductMediaRepo productMedia;
    private final ProductInfoRepo productInfos;
    private final ProductMixMatchRepo mixMatches;

    @Transactional(readOnly = true)
    @Override
    public ProductResponseDto findBySlug(String slug) {
        ProductEntity p = products.findBySlug(slug);
        if (p == null || !p.isPublished()) {
            throw new ResourceNotFoundException("Sản phẩm", "Product", slug);
        }
        ProductResponseDto dto = toDto(p);
        // Trang chi tiết storefront: SEO + thông tin bổ sung + đơn vị tính (không có thuế/giá vốn/giá sỉ).
        dto.setUnitOfMeasure(p.getUnitOfMeasure());
        dto.setMetaTitle(p.getMetaTitle());
        dto.setMetaDescription(p.getMetaDescription());
        dto.setMetaKeywords(p.getMetaKeywords());
        dto.setProductInfos(productInfos.findByProductId(p.getId()).stream().map(ProductInfoResponseDto::from).toList());
        return dto;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductResponseDto> search(String categorySlug, String collectionSlug, String keyword,
                                                     String material, BigDecimal minPrice, BigDecimal maxPrice,
                                                     String sort, int page, int take) {
        List<ProductEntity> matched = products.searchPublished(
                nullToEmpty(categorySlug), nullToEmpty(collectionSlug), nullToEmpty(keyword), nullToEmpty(material),
                minPrice != null ? minPrice : BigDecimal.ZERO,
                maxPrice != null ? maxPrice : NO_MAX_PRICE);

        List<ProductResponseDto> dtos = matched.stream().map(this::toDto).toList();
        dtos = sortDtos(dtos, sort);

        // page 1-based, take mặc định 20 tối đa 100 (quy ước chung, xem PageResponse.paginate).
        return PageResponse.paginate(dtos, page, take);
    }

    private List<ProductResponseDto> sortDtos(List<ProductResponseDto> dtos, String sort) {
        String s = sort == null ? "" : sort.trim().toUpperCase();
        Comparator<ProductResponseDto> cmp = switch (s) {
            case "PRICE_ASC" -> Comparator.comparing(ProductResponseDto::getPriceFrom,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "PRICE_DESC" -> Comparator.comparing(ProductResponseDto::getPriceFrom,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
            // "NEWEST" và "RATING_DESC" (chưa có cột rating, tạm coi như NEWEST) đều rơi vào đây.
            default -> Comparator.comparing(ProductResponseDto::getPublishedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        };
        return dtos.stream().sorted(cmp.thenComparing(ProductResponseDto::getId, Comparator.reverseOrder())).toList();
    }

    private ProductResponseDto toDto(ProductEntity p) {
        CategoryEntity category = p.getCategoryId() != null ? categories.findOne(p.getCategoryId()) : null;

        List<ProductSkuEntity> publishedSkuEntities = productSkus.findByProductId(p.getId()).stream()
                .filter(sku -> ProductSkuEntity.PUBLISHED.equals(sku.getStatus()))
                .toList();
        // Giá storefront đã trừ khuyến mãi sản phẩm (PromotionPricingService) để khớp giá giỏ/checkout:
        // khi có khuyến mãi, salePrice của SKU = giá sau khuyến mãi; listPrice giữ nguyên làm giá gạch.
        List<ProductSkuResponseDto> publishedSkus = publishedSkuEntities.stream()
                .map(sku -> {
                    ProductSkuResponseDto dto = ProductSkuResponseDto.from(sku);
                    BigDecimal price = sku.effectivePrice();
                    BigDecimal discount = price == null ? BigDecimal.ZERO
                            : promotionPricing.unitDiscount(p.getId(), price);
                    if (discount.signum() > 0) {
                        dto.setSalePrice(price.subtract(discount));
                    }
                    return dto;
                })
                .toList();
        attachBindings(p.getId(), publishedSkus);

        BigDecimal priceFrom = publishedSkus.stream()
                .map(dto -> dto.getSalePrice() != null ? dto.getSalePrice() : dto.getListPrice())
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                // Không có SKU nào đang bán (lệch dữ liệu) -> tạm lấy basePrice để không trả null.
                .orElse(p.getBasePrice());

        List<CollectionResponseDto> productCollections = collections.findByProductId(p.getId()).stream()
                .map(CollectionResponseDto::from)
                .toList();

        ProductResponseDto dto = ProductResponseDto.from(p, category != null ? category.getName() : null, priceFrom,
                publishedSkus, productCollections);
        dto.setCategorySlug(category != null ? category.getSlug() : null);
        dto.setMedia(productMedia.findByProductId(p.getId()).stream().map(ProductMediaResponseDto::from).toList());
        return dto;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    // ===================== ADMIN (mục J spec) =====================

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductResponseDto> searchForAdmin(String status, String categorySlug, String collectionSlug,
                                                             String keyword, int page, int take) {
        List<ProductEntity> matched = products.searchAdmin(nullToEmpty(status).toUpperCase(),
                nullToEmpty(categorySlug), nullToEmpty(collectionSlug), nullToEmpty(keyword));
        List<ProductResponseDto> dtos = matched.stream()
                .sorted(Comparator.comparing(ProductEntity::getId).reversed())
                .map(this::toAdminDto)
                .toList();
        return PageResponse.paginate(dtos, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public ProductResponseDto findByIdForAdmin(Long productId) {
        ProductEntity p = requireProduct(productId);
        return withDetail(toAdminDto(p), p);
    }

    @Transactional
    @Override
    public ProductResponseDto create(AdminCreateProductRequestDto req) {
        if (products.findByCode(req.getCode().trim()) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE,
                    "Mã sản phẩm đã tồn tại: " + req.getCode(), req.getCode());
        }
        String slug = (req.getSlug() == null || req.getSlug().isBlank())
                ? SlugUtil.slugify(req.getName()) : SlugUtil.slugify(req.getSlug());
        if (products.findBySlug(slug) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_SLUG, "Đường dẫn (slug) đã tồn tại: " + slug, slug);
        }
        // Kiểm tra trùng skuCode NGAY TRONG request trước (tránh vòng lặp tạo dở rồi mới phát hiện
        // trùng ở phần tử cuối), rồi mới kiểm tra trùng với DB.
        Set<String> seenCodes = new HashSet<>();
        for (AdminSkuRequestDto v : req.getVariants()) {
            String code = v.getSkuCode().trim();
            if (!seenCodes.add(code.toUpperCase())) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_SKU,
                        "Mã SKU trùng trong yêu cầu: " + code, code);
            }
            if (productSkus.findBySkuCode(code) != null) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_SKU, "Mã SKU đã tồn tại: " + code, code);
            }
        }

        ProductEntity p = new ProductEntity(req.getCategoryId(), req.getCode().trim(), req.getName().trim(), slug);
        p.setShortDescription(req.getShortDescription());
        p.setDescription(req.getDescription());
        p.setMaterial(req.getMaterial());
        p.setOccasion(req.getOccasion());
        p.setThumbnailUrl(req.getThumbnailUrl());
        applyPim(p, req, true);
        validateMixMatch(null, req.getProductMixMatch());
        products.create(p);
        saveInfos(p.getId(), req.getProductInfos());
        saveMixMatch(p.getId(), req.getProductMixMatch());

        List<ProductSkuEntity> createdSkus = new ArrayList<>();
        for (AdminSkuRequestDto v : req.getVariants()) {
            ProductSkuEntity sku = new ProductSkuEntity(v.getSkuCode().trim(), v.getSizeLabel(),
                    v.getListPrice(), v.getOnHand());
            sku.setProductId(p.getId());
            sku.setName(v.getName());
            sku.setMaterial(v.getMaterial());
            sku.setGemstone(v.getGemstone());
            sku.setMetalColorLabel(v.getMetalColorLabel());
            sku.setCaratWeight(v.getCaratWeight());
            sku.setWeightGram(v.getWeightGram());
            sku.setSalePrice(v.getSalePrice());
            sku.setDefault(v.isDefault());
            applySkuPim(sku, v);
            productSkus.create(sku);
            createdSkus.add(sku);
        }
        recalcBasePrice(p, createdSkus);

        return withDetail(toAdminDto(p, createdSkus), p);
    }

    @Transactional
    @Override
    public ProductResponseDto update(Long productId, AdminUpdateProductRequestDto req) {
        ProductEntity p = requireProduct(productId);
        if (req.getCode() != null && !req.getCode().isBlank() && !req.getCode().trim().equals(p.getCode())) {
            throw BusinessException.badRequest("CODE_IMMUTABLE",
                    "Không đổi được mã sản phẩm (hiện là " + p.getCode() + ")", req.getCode());
        }
        validateMixMatch(productId, req.getProductMixMatch());
        p.setCategoryId(req.getCategoryId());
        p.setName(req.getName().trim());
        p.setShortDescription(req.getShortDescription());
        p.setDescription(req.getDescription());
        p.setMaterial(req.getMaterial());
        p.setOccasion(req.getOccasion());
        p.setThumbnailUrl(req.getThumbnailUrl());
        applyPim(p, req, false);
        p.touch();
        products.update(p);
        if (req.getProductInfos() != null) {
            productInfos.deleteByProductId(productId);
            saveInfos(productId, req.getProductInfos());
        }
        if (req.getProductMixMatch() != null) {
            mixMatches.deleteByProductId(productId);
            saveMixMatch(productId, req.getProductMixMatch());
        }
        return withDetail(toAdminDto(p), p);
    }

    @Transactional
    @Override
    public void softDelete(Long productId) {
        ProductEntity p = requireProduct(productId);
        // deletedId (ai xoá) CHƯA gán được - service không có principal, cần truyền actor id từ Rest
        // qua @AuthenticationPrincipal nếu sau này cần audit chi tiết; để null hiện tại, tương tự
        // cách project đang bỏ ngỏ actor cho vài audit khác (xem OrderTimeline.actor).
        p.setDeletedDate(new Date());
        p.touch();
        products.update(p);
    }

    @Transactional
    @Override
    public ProductResponseDto archive(Long productId) {
        ProductEntity p = requireProduct(productId);
        p.setStatus(ProductEntity.ARCHIVED);
        p.touch();
        products.update(p);
        return toAdminDto(p);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductSkuResponseDto> findVariantsByProductId(Long productId) {
        requireProduct(productId);
        List<ProductSkuResponseDto> dtos = productSkus.findByProductId(productId).stream()
                .map(ProductSkuResponseDto::from).toList();
        attachBindings(productId, dtos);
        return dtos;
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductSkuResponseDto> searchVariants(String keyword) {
        return productSkus.searchByKeyword(nullToEmpty(keyword)).stream().map(ProductSkuResponseDto::from).toList();
    }

    private ProductEntity requireProduct(Long productId) {
        ProductEntity p = products.findOne(productId);
        if (p == null || p.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Sản phẩm", "Product", productId);
        }
        return p;
    }

    /** Giống {@link #toDto(ProductEntity)} nhưng KHÔNG lọc SKU theo PUBLISHED - admin cần thấy đủ
     * mọi biến thể (kể cả DRAFT/ARCHIVED) khi xem/sửa sản phẩm, khác trang storefront chỉ hiện SKU
     * đang bán. */
    private ProductResponseDto toAdminDto(ProductEntity p) {
        return toAdminDto(p, productSkus.findByProductId(p.getId()));
    }

    private ProductResponseDto toAdminDto(ProductEntity p, List<ProductSkuEntity> skuEntities) {
        CategoryEntity category = p.getCategoryId() != null ? categories.findOne(p.getCategoryId()) : null;

        List<ProductSkuResponseDto> skus = skuEntities.stream().map(sku -> {
            ProductSkuResponseDto d = ProductSkuResponseDto.from(sku);
            d.setCostPrice(sku.getCostPrice());
            d.setB2bWholesalePrice(sku.getB2bWholesalePrice());
            d.setTaxRateOverride(sku.getTaxRateOverride());
            d.setLength(sku.getLength());
            d.setWidth(sku.getWidth());
            d.setHeight(sku.getHeight());
            return d;
        }).toList();
        attachBindings(p.getId(), skus);

        BigDecimal priceFrom = skuEntities.stream()
                .map(ProductSkuEntity::effectivePrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(p.getBasePrice());

        List<CollectionResponseDto> productCollections = collections.findByProductId(p.getId()).stream()
                .map(CollectionResponseDto::from)
                .toList();

        ProductResponseDto dto = ProductResponseDto.from(p, category != null ? category.getName() : null, priceFrom,
                skus, productCollections);
        dto.setCategorySlug(category != null ? category.getSlug() : null);
        dto.setMedia(productMedia.findByProductId(p.getId()).stream().map(ProductMediaResponseDto::from).toList());
        dto.setUnitOfMeasure(p.getUnitOfMeasure());
        dto.setTaxRateOverride(p.getTaxRateOverride());
        dto.setWeightGram(p.getWeightGram());
        dto.setLength(p.getLength());
        dto.setWidth(p.getWidth());
        dto.setHeight(p.getHeight());
        dto.setMetaTitle(p.getMetaTitle());
        dto.setMetaDescription(p.getMetaDescription());
        dto.setMetaKeywords(p.getMetaKeywords());
        return dto;
    }

    /** Thêm productInfos + productMixMatch (chỉ ở chi tiết/tạo/sửa, tránh truy vấn thêm cho từng dòng danh sách). */
    private ProductResponseDto withDetail(ProductResponseDto dto, ProductEntity p) {
        dto.setProductInfos(productInfos.findByProductId(p.getId()).stream().map(ProductInfoResponseDto::from).toList());
        List<ProductMixMatchResponseDto> mix = new ArrayList<>();
        for (ProductMixMatchEntity m : mixMatches.findByProductId(p.getId())) {
            ProductEntity related = products.findOne(m.getRelatedProductId());
            if (related != null && related.getDeletedDate() == null) {
                mix.add(ProductMixMatchResponseDto.from(related));
            }
        }
        dto.setProductMixMatch(mix);
        return dto;
    }

    private static final java.util.Set<String> UNITS = java.util.Set.of(
            "EACH", "STRAND", "PAIR", "SET", "BOX", "BAG", "SHEET");

    /** Ghi dữ liệu PIM mở rộng từ request vào sản phẩm (creating: unitOfMeasure null -> EACH; sửa: null -> giữ nguyên). */
    private static void applyPim(ProductEntity p, AdminProductExtraRequestDto req, boolean creating) {
        if (req.getUnitOfMeasure() != null && !req.getUnitOfMeasure().isBlank()) {
            String u = req.getUnitOfMeasure().trim().toUpperCase();
            if (!UNITS.contains(u)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "unitOfMeasure không hợp lệ: " + req.getUnitOfMeasure() + " (EACH | STRAND | PAIR | SET | BOX | BAG | SHEET)",
                        req.getUnitOfMeasure());
            }
            p.setUnitOfMeasure(u);
        } else if (creating) {
            p.setUnitOfMeasure("EACH");
        }
        p.setTaxRateOverride(req.getTaxRateOverride());
        p.setWeightGram(req.getWeightGram());
        p.setLength(req.getLength());
        p.setWidth(req.getWidth());
        p.setHeight(req.getHeight());
        p.setMetaTitle(blankToNull(req.getMetaTitle()));
        p.setMetaDescription(blankToNull(req.getMetaDescription()));
        p.setMetaKeywords(blankToNull(req.getMetaKeywords()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void applySkuPim(ProductSkuEntity sku, AdminSkuRequestDto v) {
        sku.setCostPrice(v.getCostPrice());
        sku.setB2bWholesalePrice(v.getB2bWholesalePrice());
        sku.setTaxRateOverride(v.getTaxRateOverride());
        sku.setLength(v.getLength());
        sku.setWidth(v.getWidth());
        sku.setHeight(v.getHeight());
    }

    private void saveInfos(Long productId, List<AdminProductExtraRequestDto.Info> infos) {
        if (infos == null) {
            return;
        }
        int i = 0;
        for (AdminProductExtraRequestDto.Info in : infos) {
            ProductInfoEntity e = new ProductInfoEntity();
            e.setProductId(productId);
            e.setTitle(blankToNull(in.getTitle()));
            e.setDescription(in.getDescription().trim());
            e.setSortOrder(in.getSortOrder() != null ? in.getSortOrder() : i);
            productInfos.create(e);
            i++;
        }
    }

    /** Mọi id phối kèm phải là sản phẩm tồn tại (chưa xoá), không trùng, không phải chính sản phẩm đang lưu. */
    private void validateMixMatch(Long selfId, List<Long> ids) {
        if (ids == null) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id.equals(selfId)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "productMixMatch không được chứa chính sản phẩm này", id);
            }
            if (!seen.add(id)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "productMixMatch bị trùng id " + id, id);
            }
            ProductEntity related = products.findOne(id);
            if (related == null || related.getDeletedDate() != null) {
                throw new ResourceNotFoundException("Sản phẩm phối kèm", "Product", id);
            }
        }
    }

    private void saveMixMatch(Long productId, List<Long> ids) {
        if (ids == null) {
            return;
        }
        int i = 0;
        for (Long id : ids) {
            ProductMixMatchEntity e = new ProductMixMatchEntity();
            e.setProductId(productId);
            e.setRelatedProductId(id);
            e.setSortOrder(i++);
            mixMatches.create(e);
        }
    }

    // ===================== PUBLISH / SKU (admin) =====================

    private static final java.util.Set<String> SKU_STATUSES = java.util.Set.of(
            ProductSkuEntity.DRAFT, ProductSkuEntity.PUBLISHED, ProductSkuEntity.ARCHIVED);

    /** products.base_price dùng cho lọc/sắp xếp giá ở SQL storefront -> giữ bằng giá thấp nhất của các SKU chưa ARCHIVED. */
    private void recalcBasePrice(ProductEntity p, List<ProductSkuEntity> skuEntities) {
        BigDecimal min = skuEntities.stream()
                .filter(s -> !ProductSkuEntity.ARCHIVED.equals(s.getStatus()))
                .map(ProductSkuEntity::effectivePrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        if (p.getBasePrice() == null || p.getBasePrice().compareTo(min) != 0) {
            p.setBasePrice(min);
            p.touch();
            products.update(p);
        }
    }

    @Transactional
    @Override
    public ProductResponseDto publish(Long productId) {
        ProductEntity p = requireProduct(productId);
        List<ProductSkuEntity> skuList = productSkus.findByProductId(productId);
        List<ProductSkuEntity> sellable = skuList.stream()
                .filter(s -> !ProductSkuEntity.ARCHIVED.equals(s.getStatus()))
                .toList();
        if (sellable.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Không thể publish: sản phẩm chưa có SKU nào (chưa ARCHIVED)");
        }
        for (ProductSkuEntity s : sellable) {
            if (s.getListPrice() == null || s.getListPrice().signum() <= 0) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Không thể publish: SKU " + s.getSkuCode() + " chưa có giá niêm yết > 0", s.getSkuCode());
            }
        }
        for (ProductSkuEntity s : sellable) {
            if (!ProductSkuEntity.PUBLISHED.equals(s.getStatus())) {
                s.setStatus(ProductSkuEntity.PUBLISHED);
                s.touch();
                productSkus.update(s);
            }
        }
        if (sellable.stream().noneMatch(ProductSkuEntity::isDefault)) {
            ProductSkuEntity first = sellable.get(0);
            first.setDefault(true);
            first.touch();
            productSkus.update(first);
        }
        p.publish();
        p.touch();
        products.update(p);
        recalcBasePrice(p, productSkus.findByProductId(productId));
        return toAdminDto(p);
    }

    @Transactional
    @Override
    public ProductResponseDto unpublish(Long productId) {
        ProductEntity p = requireProduct(productId);
        p.setStatus(ProductEntity.DRAFT);
        p.touch();
        products.update(p);
        return toAdminDto(p);
    }

    private static String normalizeSkuStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String s = status.trim().toUpperCase();
        if (!SKU_STATUSES.contains(s)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "status SKU không hợp lệ: " + status + " (DRAFT | PUBLISHED | ARCHIVED)", status);
        }
        return s;
    }

    /** Mỗi sản phẩm chỉ 1 SKU mặc định: bỏ cờ mặc định của các SKU khác. */
    private void clearOtherDefaults(Long productId, Long keepSkuId) {
        for (ProductSkuEntity other : productSkus.findByProductId(productId)) {
            if (!other.getId().equals(keepSkuId) && other.isDefault()) {
                other.setDefault(false);
                other.touch();
                productSkus.update(other);
            }
        }
    }

    @Transactional
    @Override
    public ProductSkuResponseDto addVariant(Long productId, AdminSkuRequestDto req) {
        ProductEntity p = requireProduct(productId);
        String code = req.getSkuCode().trim();
        if (productSkus.findBySkuCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_SKU, "Mã SKU đã tồn tại: " + code, code);
        }
        ProductSkuEntity sku = new ProductSkuEntity(code, req.getSizeLabel(), req.getListPrice(), req.getOnHand());
        sku.setProductId(productId);
        applySkuFields(sku, req);
        sku.setStatus(normalizeSkuStatus(req.getStatus(), ProductSkuEntity.DRAFT));
        sku.setDefault(req.isDefault());
        applySkuPim(sku, req);
        productSkus.create(sku);
        if (sku.isDefault()) {
            clearOtherDefaults(productId, sku.getId());
        }
        recalcBasePrice(p, productSkus.findByProductId(productId));
        return skuDto(sku);
    }

    @Transactional
    @Override
    public ProductSkuResponseDto updateVariant(Long productId, Long skuId, AdminSkuRequestDto req) {
        ProductEntity p = requireProduct(productId);
        ProductSkuEntity sku = skuId == null ? null : productSkus.findOne(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            throw new ResourceNotFoundException("SKU", "ProductSku", skuId);
        }
        String code = req.getSkuCode().trim();
        if (!code.equalsIgnoreCase(sku.getSkuCode())) {
            ProductSkuEntity dup = productSkus.findBySkuCode(code);
            if (dup != null && !dup.getId().equals(skuId)) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_SKU, "Mã SKU đã tồn tại: " + code, code);
            }
            sku.setSkuCode(code);
        }
        sku.setSizeLabel(req.getSizeLabel());
        sku.setListPrice(req.getListPrice());
        applySkuFields(sku, req);
        sku.setStatus(normalizeSkuStatus(req.getStatus(), sku.getStatus()));
        applySkuPim(sku, req);
        // onHand KHÔNG đổi ở đây: tồn kho đi qua inventory (adjustments) để có lịch sử.
        boolean wasDefault = sku.isDefault();
        sku.setDefault(req.isDefault());
        sku.touch();
        productSkus.update(sku);
        if (sku.isDefault() && !wasDefault) {
            clearOtherDefaults(productId, skuId);
        }
        recalcBasePrice(p, productSkus.findByProductId(productId));
        return skuDto(sku);
    }

    private static void applySkuFields(ProductSkuEntity sku, AdminSkuRequestDto v) {
        sku.setName(v.getName());
        sku.setMaterial(v.getMaterial());
        sku.setGemstone(v.getGemstone());
        sku.setMetalColorLabel(v.getMetalColorLabel());
        sku.setCaratWeight(v.getCaratWeight());
        sku.setWeightGram(v.getWeightGram());
        sku.setSalePrice(v.getSalePrice());
    }

    // ===================== THUỘC TÍNH CỦA SKU =====================

    private ProductSkuResponseDto skuDto(ProductSkuEntity sku) {
        ProductSkuResponseDto dto = ProductSkuResponseDto.from(sku);
        attachBindings(sku.getProductId(), List.of(dto));
        return dto;
    }

    /** Nạp thuộc tính đã gán vào DTO các SKU của 1 sản phẩm (2 truy vấn cho cả sản phẩm). */
    private void attachBindings(Long productId, List<ProductSkuResponseDto> dtos) {
        if (dtos.isEmpty()) {
            return;
        }
        List<SkuAttributeValueEntity> rows = skuAttributes.findByProductId(productId);
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, ProductAttributeEntity> defs = new LinkedHashMap<>();
        for (ProductAttributeEntity a : attributeDefs.findAll(org.springframework.data.domain.Sort.unsorted())) {
            defs.put(a.getId(), a);
        }
        for (ProductSkuResponseDto dto : dtos) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (SkuAttributeValueEntity row : rows) {
                ProductAttributeEntity def = defs.get(row.getAttributeId());
                if (dto.getId().equals(row.getSkuId()) && def != null) {
                    list.add(bindingOf(def, row.getValue()));
                }
            }
            dto.setAttributeBindings(list);
        }
    }

    private static Map<String, Object> bindingOf(ProductAttributeEntity def, String stored) {
        List<String> values = ProductAttributeEntity.MULTISELECT.equals(def.getType())
                ? List.of(stored.split("\\|\\|")) : List.of(stored);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(def.getId()));
        m.put("code", def.getCode());
        m.put("name", def.getName());
        m.put("type", def.getType());
        m.put("value", String.join(", ", values));
        m.put("values", values);
        return m;
    }

    private ProductSkuEntity requireSku(Long productId, Long skuId) {
        requireProduct(productId);
        ProductSkuEntity sku = skuId == null ? null : productSkus.findOne(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            throw new ResourceNotFoundException("SKU", "ProductSku", skuId);
        }
        return sku;
    }

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> findVariantAttributes(Long productId, Long skuId) {
        return skuDto(requireSku(productId, skuId)).getAttributeBindings();
    }

    @Transactional
    @Override
    public ProductSkuResponseDto setVariantAttributes(Long productId, Long skuId, AdminSkuAttributesRequestDto req) {
        ProductSkuEntity sku = requireSku(productId, skuId);
        // Kiểm tra TOÀN BỘ trước khi ghi để lỗi không để lại trạng thái nửa vời.
        Map<Long, String> toStore = new LinkedHashMap<>();
        for (AdminSkuAttributesRequestDto.Item item : req.getValues()) {
            ProductAttributeEntity def = resolveAttribute(item);
            if (toStore.containsKey(def.getId())) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Thuộc tính bị lặp trong request: " + def.getCode(), def.getCode());
            }
            toStore.put(def.getId(), normalizeAttributeValue(def, item));
        }
        skuAttributes.deleteBySkuId(skuId);
        for (Map.Entry<Long, String> e : toStore.entrySet()) {
            skuAttributes.create(new SkuAttributeValueEntity(skuId, e.getKey(), e.getValue()));
        }
        return skuDto(sku);
    }

    private ProductAttributeEntity resolveAttribute(AdminSkuAttributesRequestDto.Item item) {
        ProductAttributeEntity def = null;
        if (item.getAttributeId() != null) {
            def = attributeDefs.findOne(item.getAttributeId());
        } else if (item.getAttributeCode() != null && !item.getAttributeCode().isBlank()) {
            def = attributeDefs.findByCode(item.getAttributeCode().trim());
        } else {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Cần attributeId hoặc attributeCode");
        }
        if (def == null) {
            throw new ResourceNotFoundException("Thuộc tính", "Attribute",
                    item.getAttributeId() != null ? item.getAttributeId() : item.getAttributeCode());
        }
        return def;
    }

    /** Trả chuỗi lưu DB: TEXT/SELECT = giá trị; MULTISELECT = các giá trị nối "||". */
    private static String normalizeAttributeValue(ProductAttributeEntity def, AdminSkuAttributesRequestDto.Item item) {
        List<String> options = def.getOptionsList();
        if (ProductAttributeEntity.MULTISELECT.equals(def.getType())) {
            List<String> raw = item.getValues() != null ? item.getValues()
                    : (item.getValue() == null ? List.of() : List.of(item.getValue().split("\\|\\|")));
            Set<String> distinct = new LinkedHashSet<>();
            for (String v : raw) {
                if (v != null && !v.isBlank()) {
                    distinct.add(v.trim());
                }
            }
            if (distinct.isEmpty()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Thuộc tính " + def.getCode() + " cần ít nhất 1 giá trị", def.getCode());
            }
            for (String v : distinct) {
                requireOption(def, options, v);
            }
            return String.join("||", distinct);
        }
        String v = item.getValue() == null ? "" : item.getValue().trim();
        if (v.isEmpty() || v.length() > 1000) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Giá trị thuộc tính " + def.getCode() + " không được trống và tối đa 1000 ký tự", def.getCode());
        }
        if (ProductAttributeEntity.SELECT.equals(def.getType())) {
            requireOption(def, options, v);
        }
        return v;
    }

    private static void requireOption(ProductAttributeEntity def, List<String> options, String value) {
        if (!options.contains(value)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Giá trị '" + value + "' không thuộc options của thuộc tính " + def.getCode() + " " + options,
                    def.getCode());
        }
    }
}

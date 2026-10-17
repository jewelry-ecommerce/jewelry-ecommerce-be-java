package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.ProductListBannerConfigRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.ProductListBannerService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminProductListBannerConfigRequestDto;
import com.pk.core.model.dto.response.ProductListBannerConfigResponseDto;
import com.pk.core.model.entity.ProductListBannerConfigEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductListBannerServiceImpl implements ProductListBannerService {

    private static final Set<String> CONDITIONS = Set.of("CATEGORY", "CMS_STOREFRONT");
    private static final Set<String> ACTIONS = Set.of("LINK_URL", "MIX_MATCH");
    private static final int MAX_BANNERS = 20;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ProductListBannerConfigRepo configs;
    private final ProductSkuRepo skus;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    @Override
    public List<ProductListBannerConfigResponseDto> list(String conditionType) {
        String c = conditionType == null ? "" : conditionType.trim().toUpperCase(Locale.ROOT);
        if (!c.isEmpty() && !CONDITIONS.contains(c)) {
            throw invalid("conditionType không hợp lệ (CATEGORY | CMS_STOREFRONT)");
        }
        return configs.findAll(Sort.unsorted()).stream()
                .filter(e -> c.isEmpty() || c.equals(e.getConditionType()))
                .sorted(Comparator.comparing(ProductListBannerConfigEntity::getId).reversed())
                .map(this::toDto).toList();
    }

    @Transactional
    @Override
    public ProductListBannerConfigResponseDto create(AdminProductListBannerConfigRequestDto req, Long actorId) {
        ProductListBannerConfigEntity e = new ProductListBannerConfigEntity();
        e.setCreatedId(actorId);
        apply(e, req, true);
        ensureUnique(e);
        configs.create(e);
        return toDto(e);
    }

    @Transactional
    @Override
    public ProductListBannerConfigResponseDto update(Long id, AdminProductListBannerConfigRequestDto req, Long actorId) {
        ProductListBannerConfigEntity e = require(id);
        apply(e, req, false);
        ensureUnique(e);
        e.setUpdatedId(actorId);
        e.touch();
        configs.update(e);
        return toDto(e);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        configs.delete(require(id));
    }

    @Transactional(readOnly = true)
    @Override
    public ProductListBannerConfigResponseDto publicConfig(String conditionType, String targetSlug) {
        String c = conditionType == null ? "" : conditionType.trim().toUpperCase(Locale.ROOT);
        String slug = targetSlug == null ? "" : targetSlug.trim();
        if (!CONDITIONS.contains(c) || slug.isEmpty()) {
            return null;
        }
        ProductListBannerConfigEntity e = configs.findAll(Sort.unsorted()).stream()
                .filter(x -> c.equals(x.getConditionType()) && slug.equals(x.getTargetSlug()) && x.isActive())
                .findFirst().orElse(null);
        if (e == null) {
            return null;
        }
        Date now = new Date();
        ProductListBannerConfigResponseDto dto = toDto(e);
        List<ProductListBannerConfigResponseDto.Banner> live = dto.banners().stream().filter(b -> b.enabled()
                && (b.startTime() == null || !Instant.parse(b.startTime()).isAfter(now.toInstant()))
                && (b.endTime() == null || now.toInstant().isBefore(Instant.parse(b.endTime())))).toList();
        if (live.isEmpty()) {
            return null;
        }
        return new ProductListBannerConfigResponseDto(dto.id(), dto.conditionType(), dto.targetId(), dto.targetSlug(),
                dto.targetName(), dto.categoryPathSlugs(), dto.isActive(), live, dto.createdAt(), dto.updatedAt());
    }

    // ------------------------------------------------------------------ helpers

    private void apply(ProductListBannerConfigEntity e, AdminProductListBannerConfigRequestDto r, boolean creating) {
        if (creating || r.getConditionType() != null) {
            String c = r.getConditionType() == null ? "" : r.getConditionType().trim().toUpperCase(Locale.ROOT);
            if (!CONDITIONS.contains(c)) {
                throw invalid("conditionType bắt buộc (CATEGORY | CMS_STOREFRONT)");
            }
            e.setConditionType(c);
        }
        if (creating || r.getTargetSlug() != null) {
            String slug = r.getTargetSlug() == null ? "" : r.getTargetSlug().trim();
            if (slug.isEmpty() || slug.length() > 200) {
                throw invalid("targetSlug bắt buộc, tối đa 200 ký tự");
            }
            e.setTargetSlug(slug);
        }
        if (r.getTargetId() != null) {
            e.setTargetId(r.getTargetId().isBlank() ? null : r.getTargetId().trim());
        }
        if (r.getTargetName() != null) {
            e.setTargetName(r.getTargetName().isBlank() ? null : r.getTargetName().trim());
        }
        if (r.getCategoryPathSlugs() != null) {
            e.setCategoryPathSlugs(r.getCategoryPathSlugs().isEmpty() ? null : write(r.getCategoryPathSlugs()));
        }
        if (r.getIsActive() != null) {
            e.setActive(r.getIsActive());
        }
        if (creating && r.getBanners() == null) {
            throw invalid("banners bắt buộc");
        }
        if (r.getBanners() != null) {
            e.setBanners(write(normalizeBanners(r.getBanners())));
        }
        if ("CMS_STOREFRONT".equals(e.getConditionType()) && (e.getTargetName() == null)) {
            throw invalid("CMS_STOREFRONT cần targetName");
        }
    }

    private List<ProductListBannerConfigResponseDto.Banner> normalizeBanners(List<AdminProductListBannerConfigRequestDto.Banner> in) {
        if (in.size() > MAX_BANNERS) {
            throw invalid("Tối đa " + MAX_BANNERS + " banner mỗi cấu hình");
        }
        List<ProductListBannerConfigResponseDto.Banner> out = new ArrayList<>();
        for (AdminProductListBannerConfigRequestDto.Banner b : in) {
            String image = b.getImageUrl() == null ? "" : b.getImageUrl().trim();
            if (image.isEmpty()) {
                throw invalid("Mỗi banner cần imageUrl");
            }
            String action = b.getActionType() == null || b.getActionType().isBlank() ? "LINK_URL" : b.getActionType().trim().toUpperCase(Locale.ROOT);
            if (!ACTIONS.contains(action)) {
                throw invalid("actionType không hợp lệ (LINK_URL | MIX_MATCH)");
            }
            String actionUrl = b.getActionUrl() == null ? null : b.getActionUrl().trim();
            List<ProductListBannerConfigResponseDto.MixMatchSku> mix = new ArrayList<>();
            if ("LINK_URL".equals(action)) {
                if (actionUrl != null && !actionUrl.isEmpty() && !(actionUrl.startsWith("http://") || actionUrl.startsWith("https://") || actionUrl.startsWith("/"))) {
                    throw invalid("actionUrl phải bắt đầu bằng http://, https:// hoặc /");
                }
            } else {
                if (b.getMixMatchSkus() == null || b.getMixMatchSkus().isEmpty()) {
                    throw invalid("MIX_MATCH cần ít nhất một SKU (mixMatchSkus)");
                }
                int i = 0;
                for (AdminProductListBannerConfigRequestDto.MixMatchSku m : b.getMixMatchSkus()) {
                    Long skuId = parseLong(m.getVariationId());
                    if (skuId == null || skus.findOne(skuId) == null) {
                        throw BusinessException.badRequest("PRODUCT_LIST_BANNER_SKU_INVALID",
                                "SKU không tồn tại: " + m.getVariationId(), m.getVariationId());
                    }
                    mix.add(new ProductListBannerConfigResponseDto.MixMatchSku(String.valueOf(skuId), m.getSortOrder() != null ? m.getSortOrder() : i));
                    i++;
                }
                mix.sort(Comparator.comparingInt(ProductListBannerConfigResponseDto.MixMatchSku::sortOrder));
            }
            Date start = parse(b.getStartTime(), "startTime");
            Date end = parse(b.getEndTime(), "endTime");
            if (start != null && end != null && !end.after(start)) {
                throw invalid("endTime phải sau startTime");
            }
            out.add(new ProductListBannerConfigResponseDto.Banner(
                    b.getId() == null || b.getId().isBlank() ? UUID.randomUUID().toString() : b.getId().trim(),
                    b.getEnabled() == null || b.getEnabled(), image, start == null ? null : start.toInstant().toString(),
                    end == null ? null : end.toInstant().toString(), action, actionUrl,
                    Boolean.TRUE.equals(b.getOpenInNewTab()), mix));
        }
        return out;
    }

    private void ensureUnique(ProductListBannerConfigEntity e) {
        boolean dup = configs.findAll(Sort.unsorted()).stream().anyMatch(x -> !x.getId().equals(e.getId())
                && x.getConditionType().equals(e.getConditionType()) && x.getTargetSlug().equals(e.getTargetSlug()));
        if (dup) {
            throw BusinessException.conflict("PRODUCT_LIST_BANNER_DUPLICATE",
                    "Đã có cấu hình banner cho đích này: " + e.getTargetSlug(), e.getTargetSlug());
        }
    }

    private ProductListBannerConfigEntity require(Long id) {
        ProductListBannerConfigEntity e = id == null ? null : configs.findOne(id);
        if (e == null) {
            throw new ResourceNotFoundException("Cấu hình banner danh sách sản phẩm", "ProductListBannerConfig", id);
        }
        return e;
    }

    private ProductListBannerConfigResponseDto toDto(ProductListBannerConfigEntity e) {
        List<ProductListBannerConfigResponseDto.Banner> banners = e.getBanners() == null ? List.of()
                : read(e.getBanners(), new TypeReference<List<ProductListBannerConfigResponseDto.Banner>>() { });
        List<String> path = e.getCategoryPathSlugs() == null ? List.of()
                : read(e.getCategoryPathSlugs(), new TypeReference<List<String>>() { });
        return new ProductListBannerConfigResponseDto(e.getId(), e.getConditionType(), e.getTargetId(), e.getTargetSlug(),
                e.getTargetName(), path, e.isActive(), banners, e.getCreatedDate().toInstant().toString(),
                (e.getUpdatedDate() == null ? e.getCreatedDate() : e.getUpdatedDate()).toInstant().toString());
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Date parse(String s, String field) {
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
        } catch (RuntimeException ex) {
            throw invalid(field + " không phải thời gian ISO-8601: " + s);
        }
    }

    private static BusinessException invalid(String message) {
        return BusinessException.badRequest("PRODUCT_LIST_BANNER_INVALID", message);
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            throw invalid("JSON không hợp lệ");
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON cấu hình banner hỏng trong DB", ex);
        }
    }
}

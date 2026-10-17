package com.pk.core.model.dto.response;

import java.util.List;

/** Cấu hình banner danh sách sản phẩm. id số; banner con có id chuỗi do server cấp. */
public record ProductListBannerConfigResponseDto(Long id, String conditionType, String targetId, String targetSlug,
                                                 String targetName, List<String> categoryPathSlugs, boolean isActive,
                                                 List<Banner> banners, String createdAt, String updatedAt) {

    public record Banner(String id, boolean enabled, String imageUrl, String startTime, String endTime,
                         String actionType, String actionUrl, boolean openInNewTab, List<MixMatchSku> mixMatchSkus) {
    }

    public record MixMatchSku(String variationId, int sortOrder) {
    }
}

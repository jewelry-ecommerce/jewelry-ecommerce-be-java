package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Tạo (POST, cần conditionType + targetSlug + banners) / sửa một phần (PATCH) cấu hình banner chèn vào danh sách sản phẩm.
 * {@code banners} gửi lên là danh sách đầy đủ (thay thế toàn bộ); banner chưa có id sẽ được cấp id.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminProductListBannerConfigRequestDto {

    /** CATEGORY | CMS_STOREFRONT. */
    private String conditionType;
    private String targetId;
    private String targetSlug;
    private String targetName;
    private List<String> categoryPathSlugs;
    private Boolean isActive;
    private List<Banner> banners;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Banner {
        private String id;
        private Boolean enabled;
        private String imageUrl;
        private String startTime;
        private String endTime;
        /** LINK_URL | MIX_MATCH. */
        private String actionType;
        private String actionUrl;
        private Boolean openInNewTab;
        private List<MixMatchSku> mixMatchSkus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MixMatchSku {
        /** Id SKU (số hoặc chuỗi số). */
        private String variationId;
        private Integer sortOrder;
    }
}

package com.pk.core.business.service;

import java.math.BigDecimal;

/**
 * Điều kiện danh sách sản phẩm admin. `status` rỗng/ALL = mọi trạng thái. `timeFilter` (TODAY | YESTERDAY | LAST_7_DAYS |
 * LAST_30_DAYS | LAST_90_DAYS | THIS_MONTH | LAST_MONTH | CUSTOM) + timeFrom/timeTo lọc theo ngày tạo; created*, updated* lọc
 * trực tiếp theo ngày tạo/cập nhật. Ngày: yyyy-MM-dd hoặc ISO date-time. Giá lọc theo giá thấp nhất của SKU (basePrice).
 * `hasPendingApproval` (true = đang chờ duyệt, false = không) và `approvalStatus` (PENDING | APPROVED | REJECTED | CANCELLED)
 * lọc theo yêu cầu duyệt mới nhất của sản phẩm.
 */
public record AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                                BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                                String timeTo, String createdFrom, String createdTo, String updatedFrom,
                                String updatedTo, int page, int take, Boolean hasPendingApproval,
                                String approvalStatus) {

    /** Chữ ký cũ (không lọc duyệt). */
    public AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                             BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                             String timeTo, String createdFrom, String createdTo, String updatedFrom,
                             String updatedTo, int page, int take) {
        this(status, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                createdFrom, createdTo, updatedFrom, updatedTo, page, take, null, null);
    }

    public AdminProductQuery withoutStatus() {
        return new AdminProductQuery(null, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter,
                timeFrom, timeTo, createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval,
                approvalStatus);
    }

    /** Bỏ cả lọc duyệt - dùng để đếm các thẻ theo duyệt. */
    public AdminProductQuery withoutStatusAndApproval() {
        return new AdminProductQuery(null, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter,
                timeFrom, timeTo, createdFrom, createdTo, updatedFrom, updatedTo, page, take, null, null);
    }
}

package com.pk.core.business.service;

import java.math.BigDecimal;

/**
 * Điều kiện danh sách sản phẩm admin. `status` rỗng/ALL = mọi trạng thái. `timeFilter` (TODAY | YESTERDAY | LAST_7_DAYS |
 * LAST_30_DAYS | LAST_90_DAYS | THIS_MONTH | LAST_MONTH | CUSTOM) + timeFrom/timeTo lọc theo ngày tạo; created*, updated* lọc
 * trực tiếp theo ngày tạo/cập nhật. Ngày: yyyy-MM-dd hoặc ISO date-time. Giá lọc theo giá thấp nhất của SKU (basePrice).
 * `hasPendingApproval` (true = đang chờ duyệt, false = không) và `approvalStatus` (PENDING | APPROVED | REJECTED | CANCELLED)
 * lọc theo yêu cầu duyệt mới nhất của sản phẩm.
 * `createdById` lọc theo người tạo sản phẩm (id tài khoản admin).
 * `orderBy` (createdAt | updatedAt; mặc định id giảm dần) + `orderType` (ASC | DESC, mặc định DESC).
 * `brandId`, `seasonId` lọc theo thương hiệu / mùa. `stockStatus` (IN_STOCK | LOW_STOCK | OUT_OF_STOCK) lọc theo tổng tồn khả dụng các SKU: 0 = hết, 1..5 = sắp hết.
 */
public record AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                                BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                                String timeTo, String createdFrom, String createdTo, String updatedFrom,
                                String updatedTo, int page, int take, Boolean hasPendingApproval,
                                String approvalStatus, String stockStatus, Long brandId, Long seasonId,
                                String orderBy, String orderType, Long createdById) {

    /** Chữ ký cũ (không lọc người tạo). */
    public AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                             BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                             String timeTo, String createdFrom, String createdTo, String updatedFrom,
                             String updatedTo, int page, int take, Boolean hasPendingApproval,
                             String approvalStatus, String stockStatus, Long brandId, Long seasonId,
                             String orderBy, String orderType) {
        this(status, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval, approvalStatus,
                stockStatus, brandId, seasonId, orderBy, orderType, null);
    }

    /** Chữ ký cũ (không sắp xếp: mặc định id giảm dần). */
    public AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                             BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                             String timeTo, String createdFrom, String createdTo, String updatedFrom,
                             String updatedTo, int page, int take, Boolean hasPendingApproval,
                             String approvalStatus, String stockStatus, Long brandId, Long seasonId) {
        this(status, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval, approvalStatus,
                stockStatus, brandId, seasonId, null, null, null);
    }

    /** Chữ ký cũ (không lọc thương hiệu/mùa). */
    public AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                             BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                             String timeTo, String createdFrom, String createdTo, String updatedFrom,
                             String updatedTo, int page, int take, Boolean hasPendingApproval,
                             String approvalStatus, String stockStatus) {
        this(status, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval, approvalStatus,
                stockStatus, null, null);
    }

    /** Chữ ký cũ (không lọc tồn kho). */
    public AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                             BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                             String timeTo, String createdFrom, String createdTo, String updatedFrom,
                             String updatedTo, int page, int take, Boolean hasPendingApproval,
                             String approvalStatus) {
        this(status, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval, approvalStatus, null, null, null);
    }

    /** Chữ ký cũ (không lọc duyệt). */
    public AdminProductQuery(String status, String categorySlug, String collectionSlug, String keyword,
                             BigDecimal minPrice, BigDecimal maxPrice, String timeFilter, String timeFrom,
                             String timeTo, String createdFrom, String createdTo, String updatedFrom,
                             String updatedTo, int page, int take) {
        this(status, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                createdFrom, createdTo, updatedFrom, updatedTo, page, take, null, null, null, null, null);
    }

    public AdminProductQuery withoutStatus() {
        return new AdminProductQuery(null, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter,
                timeFrom, timeTo, createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval,
                approvalStatus, stockStatus, brandId, seasonId, orderBy, orderType, createdById);
    }

    /** Bỏ cả lọc duyệt - dùng để đếm các thẻ theo duyệt. */
    public AdminProductQuery withoutStatusAndApproval() {
        return new AdminProductQuery(null, categorySlug, collectionSlug, keyword, minPrice, maxPrice, timeFilter,
                timeFrom, timeTo, createdFrom, createdTo, updatedFrom, updatedTo, page, take, null, null, stockStatus, brandId, seasonId, orderBy, orderType, createdById);
    }
}

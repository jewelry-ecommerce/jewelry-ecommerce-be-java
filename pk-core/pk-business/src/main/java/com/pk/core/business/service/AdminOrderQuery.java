package com.pk.core.business.service;

/**
 * Điều kiện danh sách đơn admin. Mọi trường chuỗi null/rỗng = không lọc. fromDate/toDate (yyyy-MM-dd) lọc theo
 * `dateField` (createdAt = ngày đặt, mặc định | deliveredAt = ngày giao thành công). `shippingMethod` không phân biệt
 * hoa thường và dấu gạch (Standard = STANDARD, StorePickup = STORE_PICKUP). `source`: mọi đơn hiện đều từ website
 * (WEBSITE | ONLINE). `orderBy` (createdAt | updatedAt | orderCode | grandTotal) + `orderType` (ASC | DESC, mặc định DESC).
 */
public record AdminOrderQuery(String search, String status, String paymentStatus, String fromDate, String toDate,
                              String dateField, String skuCode, String shippingMethod, String provinceName,
                              String source, String orderBy, String orderType, int page, int take) {
    /** Bỏ status/phân trang - dùng để đếm theo từng trạng thái. */
    public AdminOrderQuery withoutStatus() {
        return new AdminOrderQuery(search, null, paymentStatus, fromDate, toDate, dateField, skuCode, shippingMethod,
                provinceName, source, orderBy, orderType, page, take);
    }
}

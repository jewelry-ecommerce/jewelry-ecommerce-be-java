package com.pk.core.model.dto.response;

import java.util.List;

/**
 * Dashboard admin (khớp kiểu FE `DashboardOverview`): mọi số liệu tổng hợp từ DB thật, KHÔNG seed/giả.
 * Tiền là số (VND, không thập phân); thời gian ISO-8601; `deltaPercent` null khi kỳ trước = 0 (không so sánh được).
 */
public final class DashboardResponseDto {

    private DashboardResponseDto() {
    }

    public record Kpi(String key, String label, double value, Double deltaPercent, String deltaTone, String format,
                      String caption) {
    }

    public record Series(String name, List<Double> values) {
    }

    public record SeriesSet(List<String> labels, List<Series> series) {
    }

    public record StatusCount(String status, long count) {
    }

    public record RecentOrder(String code, String customerName, String status, String paymentStatus, String channel,
                              double grandTotal, String placedAt) {
    }

    public record LowStockRow(String skuCode, String productName, String variantLabel, String warehouseCode,
                              int available, int reorderPoint) {
    }

    public record TopProductRow(String productId, String productName, String categoryName, String image,
                                long unitsSold, double revenue) {
    }

    public record Overview(List<Kpi> kpis, SeriesSet revenue, List<StatusCount> ordersByStatus,
                           List<RecentOrder> recentOrders, List<LowStockRow> lowStock,
                           List<TopProductRow> topProducts) {
    }
}

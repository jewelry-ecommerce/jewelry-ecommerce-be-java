package com.pk.core.model.constant;

import java.util.List;

/** Catalog trang admin cố định cho phân quyền theo user (menuCode.tabCode). */
public final class PermissionPageCatalog {

    public record Page(String code, String label, String menuCode, String tabCode) {
    }

    public static final List<Page> PAGES = List.of(
            new Page("dashboard.overview", "Tổng quan", "dashboard", "overview"),
            new Page("order.list", "Danh sách đơn hàng", "order", "list"),
            new Page("order.shipping", "Vận chuyển & Giao nhận", "order", "shipping"),
            new Page("order.returns", "Đổi trả & hoàn tiền", "order", "returns"),
            new Page("product.list", "Danh sách sản phẩm", "product", "list"),
            new Page("product.category", "Danh mục", "product", "category"),
            new Page("product.collection", "Bộ sưu tập", "product", "collection"),
            new Page("product.attributes", "Thuộc tính & Bộ lọc", "product", "attributes"),
            new Page("product.reviews", "Đánh giá", "product", "reviews"),
            new Page("promotion.list", "Chương trình khuyến mãi", "promotion", "list"),
            new Page("promotion.sandbox", "Giả lập khuyến mãi", "promotion", "sandbox"),
            new Page("inventory.stock", "Tồn kho", "inventory", "stock"),
            new Page("inventory.movement", "Lịch sử điều chỉnh", "inventory", "movement"),
            new Page("content.cms_pages", "Danh sách trang CMS", "content", "cms_pages"),
            new Page("content.banner_settings", "Thiết lập banner", "content", "banner_settings"),
            new Page("customer_request.back_in_stock", "Liên hệ khi có hàng", "customer_request", "back_in_stock"),
            new Page("permission.access", "Phân quyền người dùng", "permission", "access")
    );

    public static boolean isKnown(String pageCode) {
        if (pageCode == null || pageCode.isBlank()) {
            return false;
        }
        return PAGES.stream().anyMatch(p -> p.code().equals(pageCode.trim()));
    }

    private PermissionPageCatalog() {
    }
}

package com.pk.core.model.constant.admin;

import com.pk.core.model.constant.UrlConstant;
import lombok.experimental.UtilityClass;

/**
 * Hằng route cho toàn bộ API admin. Đặt ở pk-model (gói {@code constant.admin}) — CÙNG CHỖ với
 * {@link UrlConstant}/{@code UrlIdentConstant} (người dùng chỉnh lại 2026-09-29, lần sửa thứ 3 trong
 * ngày: "sao lại tạo trong common. tạo trong model chứ" — xem claude/RULE-CODE.md mục "Admin tách
 * riêng: constant đặt ở pk-model"). Nhờ đặt cùng module với {@code UrlConstant} nên dùng lại được
 * thẳng {@code UrlConstant.Common.BASE} (không cần tự định nghĩa lại "/api" như lúc đặt ở pk-common)
 * — giống đúng cách {@code UrlIdentConstant} đã làm.
 *
 * <p>Mỗi nhóm nghiệp vụ admin (theo document/09-tong-hop-api-fe.md mục I-P) là 1 lớp lồng
 * {@code @UtilityClass}, cùng khuôn mẫu {@code UrlConstant}/{@code UrlIdentConstant} đã có (field
 * {@code public final String}, không cần {@code static}).</p>
 */
@UtilityClass
public class UrlAdminConstant {

    /** Hằng dùng chung cho mọi nhóm route admin. */
    @UtilityClass
    public static class Common {
        public final String BASE = UrlConstant.Common.BASE + "/admin";
    }

    /** AdminAuthRest (pk-api, gói api.rest.admin - mục I spec). Đã làm 2026-09-29: login/logout/me. */
    @UtilityClass
    public static class Auth {
        public final String BASE = Common.BASE + "/auth";
        public final String LOGIN = BASE + "/login";
        public final String LOGOUT = BASE + "/logout";
        public final String ME = BASE + "/me";
    }

    /** AdminCatalogRest (pk-api, gói api.rest.admin - mục J spec). Sub-path còn lại (path variable,
     * hành động archive/variants) viết trực tiếp trong @GetMapping/@PostMapping... của Rest, theo
     * đúng quy ước chung (xem RULE-CODE.md mục "Đường dẫn API dùng hằng"). */
    @UtilityClass
    public static class Catalog {
        public final String BASE = Common.BASE + "/catalog";
        public final String PRODUCTS = BASE + "/products";
        public final String PRODUCT_APPROVALS = BASE + "/product-approvals";
        public final String VARIANTS = BASE + "/variants";
        public final String CATEGORIES = BASE + "/categories";
        public final String COLLECTIONS = BASE + "/collections";
        public final String ATTRIBUTES = BASE + "/attributes";
        public final String BRANDS = BASE + "/brands";
        public final String SEARCH_PINNED = BASE + "/search-pinned-products";
        public final String SEASONS = BASE + "/seasons";
        /** POST multipart (field `file`) - upload ảnh/video dùng cho media sản phẩm, banner, CMS. */
        public final String UPLOAD = BASE + "/upload";
    }

    /** AdminImportExportRest (V24): import/export CSV sản phẩm + quản lý job. */
    @UtilityClass
    public static class Ie {
        public final String BASE = Common.BASE + "/ie";
        public final String TOPICS = BASE + "/topics";
        public final String EXPORT_PRODUCTS = BASE + "/export/products";
        public final String IMPORT_PRODUCTS = BASE + "/import/products";
        public final String JOBS = BASE + "/jobs";
        public final String JOB_STATUS = BASE + "/job/status";
    }

    /** AdminPromotionVoucherRest (pk-api - mục K spec). */
    @UtilityClass
    public static class Promotion {
        public final String BASE = Common.BASE + "/promotions";
        /** Wizard chiến dịch: nháp, bản đã phát hành, tổng quan (FE: promotion/promotion-drafts, promotion/promotions, promotion/promotion-campaigns). */
        public final String DRAFTS = BASE + "/drafts";
        public final String PUBLISHED = BASE + "/published";
        public final String CAMPAIGNS = BASE + "/campaigns";
        /** Chạy thử khuyến mãi, tra cứu voucher, tra khách theo SĐT. */
        public final String RUNTIME_EVALUATE = BASE + "/runtime/evaluate";
        public final String VOUCHER_DISCOVER = BASE + "/vouchers/discover";
        public final String VOUCHER_VALIDATE = BASE + "/vouchers/validate-code";
        public final String CUSTOMER_BY_PHONE = BASE + "/customer-audience/by-phone";
        /** Cùng nghiệp vụ discover/validate nhưng nằm trong nhóm /admin/orders/** để vai trò ORDER_MANAGER dùng được khi tạo đơn (FE: order/orders/promotion-vouchers/*). */
        /** Tra khách theo SĐT cho màn tạo đơn: nằm dưới /admin/orders nên role chỉ có ORDER_MANAGER gọi được. */
        public final String ORDER_CUSTOMER_BY_PHONE = Common.BASE + "/orders/customer-audience/by-phone";
        public final String ORDER_VOUCHER_DISCOVER = Common.BASE + "/orders/promotion-vouchers/discover";
        public final String ORDER_VOUCHER_VALIDATE = Common.BASE + "/orders/promotion-vouchers/validate-code";
    }

    /** AdminPromotionVoucherRest (pk-api - mục K spec). */
    @UtilityClass
    public static class Voucher {
        public final String BASE = Common.BASE + "/vouchers";
    }

    /** AdminOrderRest (pk-api, gói api.rest.admin - mục L spec). Sub-path path-variable (/{orderCode},
     * /{orderCode}/status, /cancel, /return, /refund) viết trực tiếp trong @GetMapping/@PatchMapping/
     * @PostMapping của Rest, theo đúng quy ước chung (RULE-CODE.md mục "Đường dẫn API dùng hằng"). */
    @UtilityClass
    public static class Order {
        public final String BASE = Common.BASE + "/orders";
    }

    /** AdminOrderReturnRest (yêu cầu đổi/trả hàng): POST .../list, POST .../quote|commit, GET/PUT /{id}, PUT /{id}/approve|reject. */
    @UtilityClass
    public static class OrderReturn {
        public final String BASE = Common.BASE + "/order-returns";
    }

    /** AdminCustomerRest (pk-api, gói api.rest.admin - mục N spec). CHỈ đọc (không có API tạo/sửa/
     * xoá khách hàng từ phía admin). */
    @UtilityClass
    public static class Customer {
        public final String BASE = Common.BASE + "/customers";
    }

    /** AdminCmsRest (pk-api, gói api.rest.admin - mục O spec). Sub-path path-variable (/{pageId},
     * /{pageId}/blocks, /{pageId}/blocks/{blockId}) viết trực tiếp trong @GetMapping/@PostMapping/
     * @PutMapping/@DeleteMapping của Rest, theo đúng quy ước chung. */
    @UtilityClass
    public static class Cms {
        public final String BASE = Common.BASE + "/cms";
        public final String PAGES = BASE + "/pages";
        public final String HEADERS = BASE + "/headers";
        public final String FOOTER = BASE + "/footer";
        public final String LOGOS = BASE + "/logos";
        public final String NAVIGATIONS = BASE + "/navigations";
    }

    /** AdminBadgeRest (pk-api, gói api.rest.admin - mục P spec). GET/POST/PUT/PATCH/DELETE cụ thể
     * theo path variable viết trực tiếp trong Rest (RULE-CODE.md mục "Đường dẫn API dùng hằng"). */
    @UtilityClass
    public static class Badge {
        public final String TEMPLATES = Common.BASE + "/badge-templates";
        public final String FLOW = Common.BASE + "/badge-flow";
    }

    /** AdminDashboardRest (pk-api) - tổng hợp số liệu thật từ DB cho trang dashboard admin. */
    @UtilityClass
    public static class Dashboard {
        public final String BASE = Common.BASE + "/dashboard";
        public final String OVERVIEW = BASE + "/overview";
        public final String REVENUE = BASE + "/revenue";
        public final String RECENT_ORDERS = BASE + "/recent-orders";
        public final String LOW_STOCK = BASE + "/low-stock";
        public final String TOP_PRODUCTS = BASE + "/top-products";
    }

    /** AdminInventoryRest (pk-api, gói api.rest.admin - mục M spec). `warehouseId` quyết định thiết
     * kế cuối cùng: xem javadoc InventoryService (pk-business) - chỉ 1 kho ngầm định "MAIN". */
    @UtilityClass
    public static class Inventory {
        public final String BASE = Common.BASE + "/inventory";
        public final String STOCK_LEVELS = BASE + "/stock-levels";
        public final String ADJUSTMENTS = BASE + "/adjustments";
    }

    /** AdminBannerRest (pk-api, gói api.rest.admin - spec FE nhóm Banner). Path variable {id} viết trực tiếp ở Rest.
     * Thêm 2026-10-06. */
    @UtilityClass
    public static class Banner {
        public final String BANNERS = Common.BASE + "/banner/banners";
        public final String PLACEMENTS = Common.BASE + "/banner/placements";
        public final String PRODUCT_LIST_CONFIGS = Common.BASE + "/banner/product-list-configs";
    }

    /** AdminCustomerRequestRest (pk-api, gói api.rest.admin - spec FE nhóm Customer Request). Sub-path /back-in-stock,
     * /order-support, /newsletter, /{id}, /{id}/status, /status/bulk viết trực tiếp ở Rest. Thêm 2026-10-06. */
    @UtilityClass
    public static class CustomerRequest {
        public final String BASE = Common.BASE + "/customer-request";
    }

    /** AdminPreOrderRest (pk-api, gói api.rest.admin - spec FE nhóm Pre-order): GET list + POST tạo cấu hình. Thêm 2026-10-06. */
    @UtilityClass
    public static class PreOrder {
        public final String BASE = Common.BASE + "/pre-orders";
    }

    /** AdminAccessRest (pk-api, gói api.rest.admin - spec FE nhóm tài khoản & phân quyền): GET /admin/users, GET /admin/roles và (mở rộng ngoài
     * spec) tạo/sửa/khoá tài khoản, gán vai trò: POST /admin/users, GET/PATCH /admin/users/{id}, PUT /admin/users/{id}/roles.
     * Thêm 2026-10-06. */
    @UtilityClass
    public static class Access {
        public final String USERS = Common.BASE + "/users";
        public final String ROLES = Common.BASE + "/roles";
    }
}

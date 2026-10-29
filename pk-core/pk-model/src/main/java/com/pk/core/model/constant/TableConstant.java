package com.pk.core.model.constant;

/** Tên các bảng (dùng trong @Table của entity). Tên cột vẫn khai báo trực tiếp trong entity.
 * Cập nhật 2026-09-29 theo schema mới (V1__init.sql thiết kế lại) - thêm bảng address/order/payment/
 * voucher/promotion (một số bảng chưa có entity tương ứng, để sẵn hằng khi làm tới). */
public final class TableConstant {

    // Identity
    public static final String ROLES = "roles";
    public static final String USERS = "users";
    public static final String REFRESH_TOKENS = "refresh_tokens";
    // OTP gửi tới SĐT cho đăng ký / quên mật khẩu (V4__phone_otps.sql, mới 2026-10-06)
    public static final String PHONE_OTPS = "phone_otps";

    // Giỏ hàng và yêu thích (V5__cart_wishlist.sql, mới 2026-10-06)
    public static final String CARTS = "carts";
    public static final String CART_ITEMS = "cart_items";
    public static final String WISHLIST_ITEMS = "wishlist_items";

    // Địa chỉ
    public static final String CUSTOMER_ADDRESSES = "customer_addresses";

    // Catalog
    public static final String CATEGORIES = "categories";
    public static final String COLLECTIONS = "collections";
    public static final String PRODUCTS = "products";
    public static final String PRODUCT_SKUS = "product_skus";

    // Đơn hàng (chưa có entity, để sẵn hằng)
    public static final String ORDERS = "orders";
    public static final String ORDER_ITEMS = "order_items";
    public static final String ORDER_TIMELINE = "order_timeline";
    public static final String ORDER_IDEMPOTENCY_KEYS = "order_idempotency_keys";

    // Giao diện chung của site (V26)
    public static final String SITE_HEADERS = "site_headers";
    public static final String SITE_FOOTER = "site_footer";
    public static final String SITE_LOGOS = "site_logos";
    public static final String CMS_NAVIGATIONS = "cms_navigations";
    public static final String CMS_NAVIGATION_BADGES = "cms_navigation_badges";
    public static final String CMS_NAVIGATION_PUBLISH = "cms_navigation_publish";
    public static final String SEARCH_PINNED_PRODUCTS = "search_pinned_products";
    public static final String PRODUCT_LIST_BANNER_CONFIGS = "product_list_banner_configs";
    public static final String PROMO_DRAFTS = "promo_drafts";
    public static final String PROMO_DRAFT_COMPONENTS = "promo_draft_components";
    public static final String PROMO_DRAFT_SCHEMAS = "promo_draft_schemas";
    public static final String PROMO_SCHEMA_COMPONENTS = "promo_schema_components";
    public static final String PROMO_VOUCHER_CODES = "promo_voucher_codes";
    public static final String PROMO_PUBLISHED = "promo_published";
public static final String ORDER_NOTES = "order_notes";
    public static final String ORDER_AUDIT_LOGS = "order_audit_logs";
    public static final String ORDER_RETURNS = "order_returns";
    public static final String ORDER_RETURN_ITEMS = "order_return_items";
    public static final String ORDER_RETURN_STATUS_HISTORY = "order_return_status_history";
    public static final String BRANDS = "brands";
    public static final String SEASONS = "seasons";

    // Thanh toán (chưa có entity, để sẵn hằng)
    public static final String PAYMENTS = "payments";

    // Voucher & khuyến mãi (chưa có entity, để sẵn hằng)
    public static final String VOUCHERS = "vouchers";
    public static final String PROMOTIONS = "promotions";

    // Admin - Catalog: thuộc tính sản phẩm (V2__admin_extensions.sql, mới 2026-09-29)
    public static final String PRODUCT_ATTRIBUTES = "product_attributes";
    public static final String SKU_ATTRIBUTE_VALUES = "sku_attribute_values";
    public static final String PRODUCT_MEDIA = "product_media";
    // V18__pim_extended_fields.sql
    public static final String PRODUCT_INFOS = "product_infos";
    public static final String CATEGORY_ATTRIBUTES = "category_attributes";
    public static final String PRODUCT_ATTRIBUTE_VALUES = "product_attribute_values";
    public static final String PRODUCT_APPROVAL_REQUESTS = "product_approval_requests";
    public static final String IMPORT_EXPORT_JOBS = "import_export_jobs";
    public static final String IMPORT_JOB_ROWS = "import_job_rows";
    public static final String PRODUCT_MIX_MATCHES = "product_mix_matches";

    // Admin - CMS (V2__admin_extensions.sql, mới 2026-09-29, chưa có entity, để sẵn hằng)
    public static final String CMS_PAGES = "cms_pages";
    public static final String CMS_BLOCKS = "cms_blocks";

    // Admin - Badge (V2__admin_extensions.sql, mới 2026-09-29, chưa có entity, để sẵn hằng)
    public static final String BADGE_TEMPLATES = "badge_templates";
    public static final String BADGE_FLOW = "badge_flow";

    // Banner (V6__banners.sql, 2026-10-06)
    public static final String BANNERS = "banners";
    public static final String BANNER_PLACEMENTS = "banner_placements";
    // V17__banner_layouts.sql
    public static final String BANNER_PLACEMENT_LAYOUTS = "banner_placement_layouts";
    public static final String BANNER_PLACEMENT_LAYOUT_ITEMS = "banner_placement_layout_items";

    // Yêu cầu khách hàng (V7__customer_requests.sql, 2026-10-06)
    public static final String CUSTOMER_REQUESTS = "customer_requests";

    // Cấu hình đặt trước (V8__pre_order_configs.sql, 2026-10-06)
    public static final String PRE_ORDER_CONFIGS = "pre_order_configs";

    // Sổ kho - điều chỉnh tồn thủ công (V37__inventory_movements.sql, 2026-10-28)
    public static final String INVENTORY_MOVEMENTS = "inventory_movements";

    // Tìm kiếm storefront: từ khoá phổ biến + lịch sử theo tài khoản (V38__search_keywords.sql, 2026-10-28)
    public static final String SEARCH_KEYWORDS = "search_keywords";
    public static final String SEARCH_HISTORY = "search_history";

    private TableConstant() {
    }
}

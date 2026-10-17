package com.pk.core.model.constant;

import lombok.experimental.UtilityClass;

/**
 * Hằng route dùng chung. Trước đây còn chứa Category/Product/Admin cho pk-api cũ (CategoryRest,
 * ProductRest, AdminCatalogRest) - đã xoá cùng lúc người dùng xoá module pk-api và domain Catalog
 * (2026-09-29, tạm dừng chức năng admin/catalog). Sau đó thiết kế lại DB + dựng lại domain Catalog
 * theo spec FE (document/09-tong-hop-api-fe.md mục C) - thêm nhóm Storefront.Product bên dưới,
 * chỉ có route storefront (đọc, không cần đăng nhập); admin/CRUD catalog CHƯA làm lại đợt này.
 * Giữ lại Common.BASE vì {@link UrlIdentConstant} (pk-identity) vẫn dùng.
 * KHÔNG chứa hằng route admin: xem {@code com.pk.core.model.constant.admin.UrlAdminConstant} - đặt
 * ở gói con `constant.admin` NGAY TRONG pk-model này (cùng module với UrlConstant/UrlIdentConstant,
 * dùng lại được Common.BASE), xem claude/RULE-CODE.md mục "Admin tách riêng: constant đặt ở pk-model"
 * (2026-09-29).
 */
@UtilityClass
public class UrlConstant {

    /** Hằng dùng chung cho cả module. */
    @UtilityClass
    public static class Common {
        /** Gốc API. */
        public final String API = "/api";
        /** Phiên bản API. */
        public final String VERSION = "/v1";
        /** Gốc TƯƠNG ĐỐI của các hằng route bên dưới (không chứa /api/v1; Rest gắn @RequestMapping(Common.API + Common.VERSION)). */
        public final String BASE = "";
    }

    /** Nhóm route công khai cho FE storefront (không yêu cầu đăng nhập). */
    @UtilityClass
    public static class Storefront {
        public final String BASE = Common.BASE + "/storefront";
        /** GET công khai - phục vụ file đã upload (ảnh/video). */
        public final String FILES = BASE + "/files";
    }

    /** ProductRest (pk-api) - danh sách/chi tiết sản phẩm, danh mục, bộ sưu tập cho storefront. */
    @UtilityClass
    public static class Product {
        public final String BASE = Storefront.BASE + "/product";
        public final String CATEGORIES = BASE + "/categories";
        public final String COLLECTIONS = BASE + "/collections";
        /** Sản phẩm ghim ở ô tìm kiếm (landing) đang hiệu lực, công khai. */
        public final String SEARCH_PINNED = BASE + "/search-pinned";
        /** Chi tiết theo slug: BASE + "/{slug}" - ghép ở Rest bằng @GetMapping(BASE + "/{slug}"). */
    }

    /** CustomerAddressRest (pk-api) - sổ địa chỉ của khách đang đăng nhập. CHƯA có GET/PUT BASE (sửa
     * hồ sơ tên/email) vì đó là dữ liệu UserEntity, thuộc phạm vi identity/auth (tạm gác lại). */
    @UtilityClass
    public static class Me {
        public final String BASE = Storefront.BASE + "/me";
        public final String ADDRESSES = BASE + "/addresses";
        /** Sửa/xoá 1 địa chỉ: ADDRESSES + "/{addressId}". */
    }

    /** OrderRest (pk-api) - đặt hàng, xem đơn, huỷ đơn của khách đang đăng nhập. */
    @UtilityClass
    public static class Order {
        public final String BASE = Storefront.BASE + "/order";
        /** Chi tiết/huỷ theo mã đơn: BASE + "/{orderCode}"(/cancel). */
        /** Đặt hàng khách vãng lai (POST) và tra cứu đơn khách vãng lai (GET GUEST + "/{code}?phone="): công khai. */
        public final String GUEST = BASE + "/guest";
    }

    /** CheckoutRest (pk-api) - xem trước giá đơn hàng, không cần đăng nhập (dựa cart context gửi
     * kèm, giống OrderRest.create). */
    @UtilityClass
    public static class Checkout {
        public final String QUOTE = Storefront.BASE + "/checkout/quote";
        /** Báo giá cho khách vãng lai (công khai). */
        public final String QUOTE_GUEST = QUOTE + "/guest";
    }

    /** PaymentRest (pk-api) - webhook callback từ cổng thanh toán, không cần đăng nhập (verify chữ
     * ký ở PaymentServiceImpl thay cho xác thực Bearer token). */
    @UtilityClass
    public static class Payment {
        public final String BASE = Storefront.BASE + "/payment";
        /** Callback: BASE + "/{paymentId}/callback". */
    }

    /** AiRest (pk-api) - gợi ý sản phẩm/set trang sức (heuristic, xem AiStylistService javadoc),
     * không cần đăng nhập. */
    @UtilityClass
    public static class Ai {
        public final String BASE = Storefront.BASE + "/ai";
        public final String STYLIST_RECOMMENDATIONS = BASE + "/stylist/recommendations";
        public final String SET_BUILDER = BASE + "/set-builder";
    }

    /** BadgeRest (pk-api) - nhãn dán sản phẩm suy ra tự động (xem BadgeService javadoc), không cần
     * đăng nhập. CHƯA có CmsRest: /storefront/cms/pages/{slug} cần nội dung do admin tạo mà admin bị
     * loại trừ đợt này - không có gì để trả, nên cố ý bỏ qua (không phải quên). */
    @UtilityClass
    public static class Badge {
        public final String BASE = Storefront.BASE + "/badge";
    }

    /** CartRest (pk-api) - giỏ hàng theo spec FE (/storefront/cart/cart, đúng 2 lần "cart" như spec).
     * Công khai cho khách vãng lai (định danh bằng header X-Guest-Cart-Id); riêng {@code /merge} bắt buộc
     * đăng nhập (xem SecurityConfig). Thêm 2026-10-06. */
    @UtilityClass
    public static class Cart {
        public final String BASE = Storefront.BASE + "/cart/cart";
    }

    /** WishlistRest (pk-api) - yêu thích theo spec FE (/storefront/product/customer/wishlist), bắt buộc
     * đăng nhập (xem SecurityConfig: rule authenticated đặt TRƯỚC rule GET công khai của Product.BASE).
     * Thêm 2026-10-06. */
    @UtilityClass
    public static class Wishlist {
        public final String BASE = Product.BASE + "/customer/wishlist";
    }

    /** CmsRest (pk-api) - trang CMS đã PUBLISHED theo slug (/storefront/cms/pages/{slug}), công khai. Sub-path
     * {slug} viết trực tiếp ở Rest. Thêm 2026-10-06. */
    @UtilityClass
    public static class Cms {
        public final String PAGES = Storefront.BASE + "/cms/pages";
        /** Header (thanh thông báo đang chạy) + footer + logo đang dùng - công khai, chỉ GET. */
        public final String SITE_LAYOUT = Storefront.BASE + "/cms/site-layout";
        /** Menu điều hướng đã xuất bản (công khai, chỉ GET): {NAVIGATION} và {NAVIGATION}/plp-rail. */
        public final String NAVIGATION = Storefront.BASE + "/cms/navigation";
    }

    /** BannerRest (pk-api) - render banner theo mã vị trí (/storefront/banner/placements/code/{code}/render), công
     * khai. Thêm 2026-10-06. */
    @UtilityClass
    public static class Banner {
        public final String PLACEMENTS_BY_CODE = Storefront.BASE + "/banner/placements/code";
        /** Banner chèn vào danh sách sản phẩm theo điều kiện (danh mục / trang CMS), công khai chỉ GET. */
        public final String PRODUCT_LIST = Storefront.BASE + "/banner/product-list";
    }

    /** PreOrderRest (pk-api) - khách xem cấu hình đặt trước đang hiệu lực (enabled/message), công khai. Mở rộng ngoài
     * spec FE để client biết khi nào cho đặt SKU hết hàng. */
    @UtilityClass
    public static class PreOrder {
        public final String CURRENT = Storefront.BASE + "/pre-order";
    }

    /** CustomerRequestRest (pk-api) - khách gửi yêu cầu (báo khi có hàng, hỗ trợ đơn, đăng ký nhận tin), công khai
     * (POST). Sub-path /back-in-stock, /order-support, /newsletter viết trực tiếp ở Rest. Thêm 2026-10-06. */
    @UtilityClass
    public static class CustomerRequest {
        public final String BASE = Storefront.BASE + "/customer-request";
    }
}

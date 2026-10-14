package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toàn bộ dữ liệu tạo qua API (admin tạo danh mục/sản phẩm -> publish -> storefront -> giỏ hàng), không dùng entity/SQL
 * để vượt bước thiếu. Chỉ tài khoản admin được tạo trực tiếp (hệ thống không có API đăng ký admin).
 */
class PimPublishToCartFlowIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String CART = V1 + UrlConstant.Cart.BASE;
    private static final String GUEST_HEADER = "X-Guest-Cart-Id";

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String admin() throws Exception {
        return bearer(adminAccessToken());
    }

    private long createCategory(String auth, String t) throws Exception {
        return body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", auth))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Tạo sản phẩm qua API với 1 SKU (listPrice 1.000.000, tồn 5). Trả [productId, skuId]. */
    private long[] createProduct(String auth, long categoryId, String t) throws Exception {
        JsonNode p = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "sizeLabel", "M", "listPrice", 1_000_000,
                                "onHand", 5, "isDefault", true)))).header("Authorization", auth))
                .andExpect(status().isCreated()));
        long productId = p.get("id").asLong();
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", auth))
                .andExpect(status().isOk())).get(0).get("id").asLong();
        return new long[] {productId, skuId};
    }

    @Test
    void newProductIsInvisibleAndNotBuyableUntilPublishedThenUnpublishHidesAgain() throws Exception {
        String auth = admin();
        String t = tag();
        long[] ids = createProduct(auth, createCategory(auth, t), t);

        // DRAFT: storefront không thấy, giỏ hàng không nhận.
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "/nhan-" + t)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(post(CART), Map.of("clearAll", false, "items",
                        List.of(Map.of("variationId", String.valueOf(ids[1]), "quantity", 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.reason").value("NOT_FOUND"));

        // Publish qua API.
        mvc.perform(patch(CATALOG + "/" + ids[0] + "/publish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "/nhan-" + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("nhan-" + t));
        // base_price được tính từ SKU nên lọc theo giá thấy được sản phẩm.
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "?category=dm-" + t + "&minPrice=900000&maxPrice=1100000"))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        // Mua được.
        mvc.perform(jsonRequest(post(CART), Map.of("clearAll", false, "items",
                        List.of(Map.of("variationId", String.valueOf(ids[1]), "quantity", 1)))))
                .andExpect(jsonPath("$.data.success").value(true));

        // Unpublish -> biến mất khỏi storefront.
        mvc.perform(patch(CATALOG + "/" + ids[0] + "/unpublish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "/nhan-" + t)).andExpect(status().isNotFound());
    }

    @Test
    void publishRejectsProductWithoutPricedSku() throws Exception {
        String auth = admin();
        String t = tag();
        long categoryId = createCategory(auth, t);
        JsonNode p = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Dây " + t, "slug", "day-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 0, "onHand", 1)))).header("Authorization", auth))
                .andExpect(status().isCreated()));
        mvc.perform(patch(CATALOG + "/" + p.get("id").asLong() + "/publish").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addAndUpdateVariantAfterCreateRecalculatesPriceAndKeepsSingleDefault() throws Exception {
        String auth = admin();
        String t = tag();
        long[] ids = createProduct(auth, createCategory(auth, t), t);
        mvc.perform(patch(CATALOG + "/" + ids[0] + "/publish").header("Authorization", auth)).andExpect(status().isOk());

        // Thêm SKU rẻ hơn, mặc định, đã PUBLISHED.
        JsonNode added = body(mvc.perform(jsonRequest(post(CATALOG + "/" + ids[0] + "/variants"), Map.of(
                        "skuCode", "SKU2-" + t, "sizeLabel", "L", "listPrice", 700_000, "onHand", 3,
                        "isDefault", true, "status", "PUBLISHED")).header("Authorization", auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED")));
        long sku2 = added.get("id").asLong();

        JsonNode variants = body(mvc.perform(get(CATALOG + "/" + ids[0] + "/variants").header("Authorization", auth))
                .andExpect(status().isOk()));
        int defaults = 0;
        for (JsonNode v : variants) {
            if (v.path("isDefault").asBoolean(false) || v.path("default").asBoolean(false)) {
                defaults++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, defaults, "chỉ 1 SKU mặc định");

        // base_price = giá thấp nhất (700.000): lọc maxPrice 800.000 thấy sản phẩm.
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "?category=dm-" + t + "&maxPrice=800000"))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // Sửa giá SKU2 lên 1.500.000 -> base_price quay về 1.000.000 (SKU1).
        mvc.perform(jsonRequest(put(CATALOG + "/" + ids[0] + "/variants/" + sku2), Map.of(
                        "skuCode", "SKU2-" + t, "sizeLabel", "L", "listPrice", 1_500_000, "onHand", 3,
                        "status", "PUBLISHED")).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listPrice").value(1_500_000));
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "?category=dm-" + t + "&maxPrice=800000"))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // Trùng mã SKU -> 409; SKU của sản phẩm khác -> 404.
        mvc.perform(jsonRequest(post(CATALOG + "/" + ids[0] + "/variants"), Map.of(
                        "skuCode", "SKU2-" + t, "listPrice", 1, "onHand", 1)).header("Authorization", auth))
                .andExpect(status().isConflict());
        mvc.perform(jsonRequest(put(CATALOG + "/" + ids[0] + "/variants/999999999"), Map.of(
                        "skuCode", "X-" + t, "listPrice", 1, "onHand", 1)).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void collectionMembershipIsManagedByApi() throws Exception {
        String auth = admin();
        String t = tag();
        long[] ids = createProduct(auth, createCategory(auth, t), t);
        mvc.perform(patch(CATALOG + "/" + ids[0] + "/publish").header("Authorization", auth)).andExpect(status().isOk());
        long collectionId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.COLLECTIONS),
                        Map.of("name", "BST " + t, "slug", "bst-" + t, "status", "PUBLISHED")).header("Authorization", auth))
                .andExpect(status().isCreated())).get("id").asLong();

        String membership = V1 + UrlAdminConstant.Catalog.COLLECTIONS + "/" + collectionId + "/products";
        mvc.perform(jsonRequest(post(membership), Map.of("productIds", List.of(ids[0], ids[0]))).header("Authorization", auth))
                .andExpect(status().isOk());
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "?collection=bst-" + t))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("nhan-" + t));

        mvc.perform(jsonRequest(post(membership), Map.of("productIds", List.of(999_999_999L))).header("Authorization", auth))
                .andExpect(status().isNotFound());

        mvc.perform(delete(membership + "/" + ids[0]).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "?collection=bst-" + t))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void cartSetQuantityIsAbsoluteAndZeroOrDeleteRemovesLine() throws Exception {
        String auth = admin();
        String t = tag();
        long[] ids = createProduct(auth, createCategory(auth, t), t);
        mvc.perform(patch(CATALOG + "/" + ids[0] + "/publish").header("Authorization", auth)).andExpect(status().isOk());
        String sku = String.valueOf(ids[1]);

        String guestId = body(mvc.perform(jsonRequest(post(CART), Map.of("clearAll", false, "items",
                        List.of(Map.of("variationId", sku, "quantity", 1)))))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1))).get("guestId").asText();

        // Tăng 1 -> 2: giá trị CUỐI là 2 (không phải 3).
        mvc.perform(jsonRequest(put(CART), Map.of("variationId", sku, "quantity", 2)).header(GUEST_HEADER, guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
        // Giảm 2 -> 1.
        mvc.perform(jsonRequest(put(CART), Map.of("variationId", sku, "quantity", 1)).header(GUEST_HEADER, guestId))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1));
        // Vượt tồn kho (5) -> hạ về 5, success=false.
        mvc.perform(jsonRequest(put(CART), Map.of("variationId", sku, "quantity", 50)).header(GUEST_HEADER, guestId))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.reason").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5));
        // quantity = 0 -> xoá dòng.
        mvc.perform(jsonRequest(put(CART), Map.of("variationId", sku, "quantity", 0)).header(GUEST_HEADER, guestId))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // DELETE một dòng, không đụng dòng khác.
        mvc.perform(jsonRequest(put(CART), Map.of("variationId", sku, "quantity", 2)).header(GUEST_HEADER, guestId))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(delete(CART + "/" + sku).header(GUEST_HEADER, guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        // Xoá dòng không có trong giỏ: bỏ qua, không lỗi.
        mvc.perform(delete(CART + "/" + sku).header(GUEST_HEADER, guestId)).andExpect(status().isOk());
    }

    @Test
    void placingOrderRemovesOnlyOrderedLinesFromCart() throws Exception {
        String auth = admin();
        String t = tag();
        long categoryId = createCategory(auth, t);
        long[] a = createProduct(auth, categoryId, t + "a");
        long[] b = createProduct(auth, categoryId, t + "b");
        for (long[] x : List.of(a, b)) {
            mvc.perform(patch(CATALOG + "/" + x[0] + "/publish").header("Authorization", auth)).andExpect(status().isOk());
        }

        String customer = bearer(customerAccessToken());
        for (long[] x : List.of(a, b)) {
            mvc.perform(jsonRequest(post(CART), Map.of("clearAll", false, "items",
                            List.of(Map.of("variationId", String.valueOf(x[1]), "quantity", 1))))
                            .header("Authorization", customer))
                    .andExpect(jsonPath("$.data.success").value(true));
        }
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of(
                        "recipientName", "Nguyễn Văn A", "phone", "0901234567", "province", "HCM", "district", "Q1",
                        "ward", "P1", "addressLine", "1 Lê Lợi")).header("Authorization", customer))
                .andExpect(status().isCreated())).get("id").asLong();

        mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of(
                        "addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", a[1], "quantity", 1)))).header("Authorization", customer))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get(CART).header("Authorization", customer))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].variationId").value(String.valueOf(b[1])));
    }

    @Test
    void unpublishedProductCannotBeQuotedOrOrderedFromAnOldCartAndStockIsUntouched() throws Exception {
        String auth = admin();
        String t = tag();
        long[] ids = createProduct(auth, createCategory(auth, t), t);
        // SKU phải PUBLISHED để bán được; publish sản phẩm đưa SKU lên PUBLISHED.
        mvc.perform(patch(CATALOG + "/" + ids[0] + "/publish").header("Authorization", auth)).andExpect(status().isOk());

        String customer = bearer(customerAccessToken());
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> req = Map.of("addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                "items", List.of(Map.of("skuId", ids[1], "quantity", 1)));

        // Đang bán: quote OK và thêm giỏ OK.
        mvc.perform(jsonRequest(post(V1 + UrlConstant.Checkout.QUOTE), req).header("Authorization", customer))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(post(CART), Map.of("clearAll", false, "items",
                        List.of(Map.of("variationId", String.valueOf(ids[1]), "quantity", 1))))
                        .header("Authorization", customer))
                .andExpect(jsonPath("$.data.success").value(true));

        mvc.perform(patch(CATALOG + "/" + ids[0] + "/unpublish").header("Authorization", auth)).andExpect(status().isOk());

        // Ngừng bán: giỏ cũ báo dòng không còn bán, quote/đặt đơn bị từ chối, không giữ tồn, không tạo đơn.
        mvc.perform(get(CART).header("Authorization", customer))
                .andExpect(jsonPath("$.data.items[0].status").value("UNPUBLISHED"));
        mvc.perform(jsonRequest(post(V1 + UrlConstant.Checkout.QUOTE), req).header("Authorization", customer))
                .andExpect(status().isNotFound());
        mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), req).header("Authorization", customer))
                .andExpect(status().isNotFound());
        mvc.perform(get(V1 + UrlConstant.Order.BASE).header("Authorization", customer))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(get(CATALOG + "/" + ids[0] + "/variants").header("Authorization", auth))
                .andExpect(jsonPath("$.data[0].availableStock").value(5));
        // Thêm vào giỏ cũng bị từ chối.
        mvc.perform(jsonRequest(post(CART), Map.of("clearAll", false, "items",
                        List.of(Map.of("variationId", String.valueOf(ids[1]), "quantity", 1)))))
                .andExpect(jsonPath("$.data.success").value(false));
    }
}

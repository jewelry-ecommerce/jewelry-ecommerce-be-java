package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khuyến mãi theo sản phẩm + voucher: validate đầu vào (400 DISCOUNT_INVALID, không 500), quy tắc áp dụng khi báo giá/đặt đơn. */
class PromotionVoucherRulesIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String PROMOS = V1 + UrlAdminConstant.Promotion.BASE;
    private static final String VOUCHERS = V1 + UrlAdminConstant.Voucher.BASE;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;

    private String admin;
    private long productId;
    private long skuId;

    private void setUp() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 1_000_000, "onHand", 20,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());
    }

    private Map<String, Object> promo(String type, Object value, long... productIds) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "KM " + UUID.randomUUID().toString().substring(0, 6));
        m.put("discountType", type);
        m.put("discountValue", value);
        m.put("startsAt", Instant.now().minus(1, ChronoUnit.HOURS).toString());
        m.put("endsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        m.put("status", "PUBLISHED");
        m.put("productIds", java.util.Arrays.stream(productIds).boxed().toList());
        return m;
    }

    private Map<String, Object> voucher(String code, String type, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("discountType", type);
        m.put("discountValue", value);
        m.put("startsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        m.put("endsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        m.put("status", "PUBLISHED");
        return m;
    }

    private void badRequest(String url, Map<String, Object> body, String codeExpected) throws Exception {
        mvc.perform(jsonRequest(post(url), body).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(codeExpected));
    }

    @Test
    void promotionInputIsValidatedWith400NotDbErrors() throws Exception {
        setUp();
        badRequest(PROMOS, promo("BOGUS", 10, productId), "DISCOUNT_INVALID");
        badRequest(PROMOS, promo("PERCENT", 101, productId), "DISCOUNT_INVALID");
        badRequest(PROMOS, promo("PERCENT", 10, 999_999_999L), "DISCOUNT_INVALID");
        badRequest(PROMOS, promo("PERCENT", 10, productId, productId), "DISCOUNT_INVALID");
        Map<String, Object> reversed = promo("FIXED", 1000, productId);
        reversed.put("endsAt", Instant.now().minus(2, ChronoUnit.DAYS).toString());
        badRequest(PROMOS, reversed, "DISCOUNT_INVALID");
        Map<String, Object> badStatus = promo("FIXED", 1000, productId);
        badStatus.put("status", "ACTIVE");
        badRequest(PROMOS, badStatus, "DISCOUNT_INVALID");
        Map<String, Object> negMax = promo("PERCENT", 10, productId);
        negMax.put("maxDiscountAmount", -1);
        badRequest(PROMOS, negMax, "DISCOUNT_INVALID");
        // Thiếu sản phẩm -> 400 (bean validation).
        Map<String, Object> noProducts = promo("PERCENT", 10, productId);
        noProducts.put("productIds", List.of());
        mvc.perform(jsonRequest(post(PROMOS), noProducts).header("Authorization", admin)).andExpect(status().isBadRequest());

        // Hợp lệ: tạo, đọc, sửa (đổi sản phẩm), xoá mềm -> 404.
        JsonNode created = body(mvc.perform(jsonRequest(post(PROMOS), promo("percent", 15, productId)).header("Authorization", admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.discountType").value("PERCENT")));
        long id = created.get("id").asLong();
        mvc.perform(jsonRequest(put(PROMOS + "/" + id), promo("FIXED", 5000, productId)).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.discountType").value("FIXED"));
        mvc.perform(jsonRequest(put(PROMOS + "/" + id), promo("BOGUS", 5000, productId)).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PROMOS + "/" + id).header("Authorization", admin)).andExpect(jsonPath("$.data.discountType").value("FIXED"));
        mvc.perform(delete(PROMOS + "/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get(PROMOS + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get(PROMOS)).andExpect(status().isUnauthorized());
        mvc.perform(get(PROMOS).header("Authorization", bearer(customerAccessToken()))).andExpect(status().isForbidden());
    }

    @Test
    void voucherInputIsValidatedAndFlatPriceRejected() throws Exception {
        setUp();
        String code = "VR" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        badRequest(VOUCHERS, voucher(code, "FLAT_PRICE", 10), "DISCOUNT_INVALID");
        badRequest(VOUCHERS, voucher(code, "BOGUS", 10), "DISCOUNT_INVALID");
        badRequest(VOUCHERS, voucher(code, "PERCENT", 150), "DISCOUNT_INVALID");
        badRequest(VOUCHERS, voucher("a b", "FIXED", 10), "DISCOUNT_INVALID");
        badRequest(VOUCHERS, voucher("AB", "FIXED", 10), "DISCOUNT_INVALID");
        Map<String, Object> zeroLimit = voucher(code, "FIXED", 10);
        zeroLimit.put("usageLimit", 0);
        badRequest(VOUCHERS, zeroLimit, "DISCOUNT_INVALID");
        Map<String, Object> period = voucher(code, "FIXED", 10);
        period.put("endsAt", Instant.now().minus(5, ChronoUnit.DAYS).toString());
        badRequest(VOUCHERS, period, "DISCOUNT_INVALID");

        // Mã được chuẩn hoá chữ hoa; trùng mã -> 409.
        mvc.perform(jsonRequest(post(VOUCHERS), voucher(code.toLowerCase(), "FIXED", 10_000)).header("Authorization", admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value(code));
        mvc.perform(jsonRequest(post(VOUCHERS), voucher(code, "FIXED", 10_000)).header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CODE"));
    }

    @Test
    void voucherRulesApplyAtQuoteAndUsageLimitAtOrder() throws Exception {
        setUp();
        String customer = bearer(customerAccessToken());
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
        String sfx = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Map<String, Object> pct = voucher("PCT" + sfx, "PERCENT", 10);
        pct.put("maxDiscountAmount", 50_000);
        pct.put("minOrderValue", 1_500_000);
        mvc.perform(jsonRequest(post(VOUCHERS), pct).header("Authorization", admin)).andExpect(status().isCreated());

        // Đủ điều kiện: 10% của 2.000.000 = 200.000 nhưng chặn trần 50.000.
        quote(customer, addressId, 2, "PCT" + sfx).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.voucherDiscount").value(50_000))
                .andExpect(jsonPath("$.data.summary.grandTotal").value(1_950_000));
        // Chưa đạt đơn tối thiểu (1 x 1.000.000 < 1.500.000).
        quote(customer, addressId, 1, "PCT" + sfx).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VOUCHER_MIN_ORDER_NOT_MET"));

        Map<String, Object> expired = voucher("EXP" + sfx, "FIXED", 10_000);
        expired.put("startsAt", Instant.now().minus(10, ChronoUnit.DAYS).toString());
        expired.put("endsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        mvc.perform(jsonRequest(post(VOUCHERS), expired).header("Authorization", admin)).andExpect(status().isCreated());
        quote(customer, addressId, 1, "EXP" + sfx).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VOUCHER_EXPIRED"));

        Map<String, Object> future = voucher("FUT" + sfx, "FIXED", 10_000);
        future.put("startsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString());
        future.put("endsAt", Instant.now().plus(5, ChronoUnit.DAYS).toString());
        mvc.perform(jsonRequest(post(VOUCHERS), future).header("Authorization", admin)).andExpect(status().isCreated());
        quote(customer, addressId, 1, "FUT" + sfx).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VOUCHER_NOT_STARTED"));

        Map<String, Object> draft = voucher("DRF" + sfx, "FIXED", 10_000);
        draft.put("status", "DRAFT");
        mvc.perform(jsonRequest(post(VOUCHERS), draft).header("Authorization", admin)).andExpect(status().isCreated());
        quote(customer, addressId, 1, "DRF" + sfx).andExpect(status().isNotFound());
        quote(customer, addressId, 1, "KHONGCO" + sfx).andExpect(status().isNotFound());

        // FIXED lớn hơn đơn không làm tổng âm.
        mvc.perform(jsonRequest(post(VOUCHERS), voucher("BIG" + sfx, "FIXED", 9_000_000)).header("Authorization", admin))
                .andExpect(status().isCreated());
        quote(customer, addressId, 1, "BIG" + sfx).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.voucherDiscount").value(1_000_000));

        // Giới hạn 1 lượt: đơn đầu dùng được, đơn sau bị từ chối và không giữ chỗ tồn.
        Map<String, Object> once = voucher("ONE" + sfx, "FIXED", 20_000);
        once.put("usageLimit", 1);
        long onceId = body(mvc.perform(jsonRequest(post(VOUCHERS), once).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
        order(customer, addressId, "ONE" + sfx).andExpect(status().isCreated());
        order(customer, addressId, "ONE" + sfx).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VOUCHER_USAGE_LIMIT_REACHED"));
        // Không thể hạ giới hạn xuống dưới số lượt đã dùng; xoá voucher đã dùng vẫn được (đơn cũ giữ snapshot mã).
        Map<String, Object> lower = voucher("ONE" + sfx, "FIXED", 20_000);
        lower.put("usageLimit", 1);
        mvc.perform(jsonRequest(put(VOUCHERS + "/" + onceId), lower).header("Authorization", admin)).andExpect(status().isOk());
        int available = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("availableStock").asInt();
        assertEquals(19, available, "chỉ đơn thành công mới giữ chỗ tồn");
    }

    private org.springframework.test.web.servlet.ResultActions quote(String customer, long addressId, int qty, String code) throws Exception {
        return mvc.perform(jsonRequest(post(V1 + UrlConstant.Checkout.QUOTE), Map.of("addressId", addressId, "shippingMethod", "STANDARD",
                "paymentMethod", "COD", "voucherCode", code, "items", List.of(Map.of("skuId", skuId, "quantity", qty))))
                .header("Authorization", customer));
    }

    private org.springframework.test.web.servlet.ResultActions order(String customer, long addressId, String code) throws Exception {
        return mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", addressId, "shippingMethod", "STANDARD",
                "paymentMethod", "COD", "voucherCode", code, "items", List.of(Map.of("skuId", skuId, "quantity", 1))))
                .header("Authorization", customer));
    }
}

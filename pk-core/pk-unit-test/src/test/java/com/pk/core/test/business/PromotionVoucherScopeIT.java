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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy lỗi FE v7: voucher sinh từ chiến dịch phải giữ target SKU ở checkout - voucher của SKU A không được giảm cho
 * giỏ chỉ có SKU B; giỏ hỗn hợp chỉ giảm trên phần của SKU A; giá trị tối thiểu tính trên phần đủ điều kiện.
 * Kiểm cả báo giá admin, tạo đơn admin, báo giá storefront (khách vãng lai) và voucher cũ (không phạm vi) giữ nguyên.
 */
class PromotionVoucherScopeIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String DRAFTS = V1 + UrlAdminConstant.Promotion.DRAFTS;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    private String admin;

    /** Tạo sản phẩm 1 SKU; trả {productId, skuId}. */
    private long[] product(String t, String s, int price) throws Exception {
        long cat = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM" + s + t, "slug", "dm" + s + "-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long id = body(mvc.perform(jsonRequest(post(CATALOG), Map.of("categoryId", cat, "code", "VS" + s + "-" + t, "name", "VS " + s + t,
                "slug", "vs" + s + "-" + t, "variants", List.of(Map.of("skuCode", "VS" + s + "-" + t, "listPrice", price, "onHand", 50,
                        "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
        long sku = body(mvc.perform(get(CATALOG + "/" + id + "/variants").header("Authorization", admin))).get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + id + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        return new long[]{id, sku};
    }

    /** Phát hành chiến dịch chỉ có 1 voucher PERCENT 10% (điều kiện tối thiểu `min` trên phần đủ điều kiện). */
    private void publishVoucher(String code, Map<String, Object> targetConfig, int min) throws Exception {
        Instant now = Instant.now();
        String d = body(mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "VS " + code, "description", "x",
                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(), "endsAt", now.plus(7, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        String sid = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "Voucher 10%", "schemaType", "VOUCHER",
                "costSources", List.of("ECOMMERCE"))).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/condition"), Map.of("configType", "TARGET_SUBTOTAL",
                "configVersion", "1.0.0", "values", Map.of("operator", "GREATER_THAN_OR_EQUAL", "amountMinor", min)))
                .header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/benefit"), Map.of("configType", "VOUCHER",
                "configVersion", "1.0.0", "values", Map.of("discountKind", "PERCENT", "discountValue", 10)))
                .header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/voucher-codes"), Map.of("code", code, "status", "ACTIVE",
                "visibilityDistributionPolicy", Map.of("type", "VOUCHER_VISIBILITY_DISTRIBUTION_POLICY", "version", "1.0.0",
                        "visibilityType", "PUBLIC", "distributionChannels", List.of()))).header("Authorization", admin))
                .andExpect(status().isOk());
        String comp = DRAFTS + "/" + d;
        mvc.perform(jsonRequest(put(comp + "/target"), Map.of("config", targetConfig)).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(jsonRequest(put(comp + "/audience"), Map.of("config", Map.of("type", "ALL", "version", "1.0.0"))).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(put(comp + "/applicability"), Map.of("config", Map.of("type", "CHANNEL_STORE_APPLICABILITY", "version", "1.0.0",
                "channels", List.of("ONLINE"), "stores", Map.of("type", "ALL", "version", "1.0.0")))).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(post(comp + "/publish").header("Authorization", admin)).andExpect(status().isOk());
    }

    private Map<String, Object> cart(String code, long[]... linesAndQty) {
        // phần tử: {skuId, qty}
        Map<String, Object> m = new HashMap<>();
        m.put("contextVersion", 2);
        m.put("receiverPhone", "0912345678");
        m.put("firstName", "Khách Test");
        m.put("provinceName", "Hồ Chí Minh");
        m.put("wardCode", 1);
        m.put("wardName", "Phường 1");
        m.put("addressLine", "1 Lê Lợi");
        m.put("shippingMethod", "STANDARD");
        m.put("paymentMethod", "COD");
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (long[] l : linesAndQty) {
            items.add(Map.of("variationId", String.valueOf(l[0]), "quantity", (int) l[1]));
        }
        m.put("items", items);
        m.put("couponCodes", List.of(code));
        return m;
    }

    private JsonNode quote(Map<String, Object> req) throws Exception {
        return body(mvc.perform(jsonRequest(post(ORDERS + "/pricing-context/quote"), req).header("Authorization", admin))
                .andExpect(status().isOk()));
    }

    private long discount(JsonNode q) {
        return q.get("pricing").get("discountTotal").asLong();
    }

    private String reason(JsonNode q) {
        JsonNode o = q.get("couponOutcomes").get(0);
        return o.get("status").asText() + ":" + o.get("reasonCodes");
    }

    @Test
    void campaignVoucherKeepsSkuTargetAtQuoteCreateAndStorefront() throws Exception {
        admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        long[] a = product(t, "A", 100_000);
        long[] b = product(t, "B", 50_000);
        String code = "SC" + t.toUpperCase();
        // voucher 10% cho SKU A, tối thiểu 80k trên phần đủ điều kiện
        publishVoucher(code, Map.of("type", "MANUAL", "version", "1.0.0", "skuIds", List.of(String.valueOf(a[1])),
                "lineFilter", Map.of("discountState", "ANY")), 80_000);

        // giỏ chỉ SKU A (100k): giảm 10% = 10k
        JsonNode onlyA = quote(cart(code, new long[]{a[1], 1}));
        assertEquals("APPLIED", onlyA.get("couponOutcomes").get(0).get("status").asText());
        assertEquals(10_000, discount(onlyA));

        // giỏ chỉ SKU B (đủ 200k nhưng không có dòng đủ điều kiện): KHÔNG giảm
        JsonNode onlyB = quote(cart(code, new long[]{b[1], 4}));
        assertTrue(reason(onlyB).startsWith("REJECTED"), reason(onlyB));
        assertTrue(reason(onlyB).contains("VOUCHER_NO_ELIGIBLE_ITEMS"), reason(onlyB));
        assertEquals(0, discount(onlyB));

        // giỏ hỗn hợp: chỉ giảm trên phần SKU A (10% của 100k, không phải 10% của 300k)
        JsonNode mixed = quote(cart(code, new long[]{a[1], 1}, new long[]{b[1], 4}));
        assertEquals(10_000, discount(mixed));
        assertEquals(300_000, mixed.get("pricing").get("subtotal").asLong());
        assertEquals(290_000, mixed.get("pricing").get("grandTotal").asLong());

        // tối thiểu tính trên phần đủ điều kiện: A x1 = 100k >= 80k ok; nhưng nếu A rẻ hơn mức tối thiểu thì từ chối
        // dù giỏ có B làm tổng cao (sửa SKU A xuống 70k)
        Map<String, Object> upd = new HashMap<>();
        upd.put("skuCode", "VSA-" + t);
        upd.put("listPrice", 70_000);
        mvc.perform(jsonRequest(put(CATALOG + "/" + a[0] + "/variants/" + a[1]), upd).header("Authorization", admin)).andExpect(status().isOk());
        JsonNode belowMin = quote(cart(code, new long[]{a[1], 1}, new long[]{b[1], 4}));
        assertTrue(reason(belowMin).contains("VOUCHER_MIN_ORDER_NOT_MET"), reason(belowMin));
        assertEquals(0, discount(belowMin));
        upd.put("listPrice", 100_000);
        mvc.perform(jsonRequest(put(CATALOG + "/" + a[0] + "/variants/" + a[1]), upd).header("Authorization", admin)).andExpect(status().isOk());

        // tạo đơn admin: giỏ chỉ SKU B với mã -> 400 (không có dòng đủ điều kiện); giỏ hỗn hợp -> giảm đúng phần A
        Map<String, Object> createB = cart(code, new long[]{b[1], 4});
        createB.put("idempotencyKey", "k-" + UUID.randomUUID());
        mvc.perform(jsonRequest(post(ORDERS), createB).header("Authorization", admin)).andExpect(status().isBadRequest());
        Map<String, Object> createMixed = cart(code, new long[]{a[1], 1}, new long[]{b[1], 4});
        createMixed.put("idempotencyKey", "k-" + UUID.randomUUID());
        JsonNode orderMixed = body(mvc.perform(jsonRequest(post(ORDERS), createMixed).header("Authorization", admin))
                .andExpect(status().isCreated()));
        assertEquals(10_000, orderMixed.get("voucherDiscount").asLong());
        assertEquals(290_000, orderMixed.get("grandTotal").asLong());

        // storefront (khách vãng lai): báo giá giỏ chỉ B không có giảm giá, giỏ có A giảm đúng phần A
        mvc.perform(jsonRequest(post(V1 + UrlConstant.Checkout.QUOTE_GUEST), guest(code, b[1], 4))).andExpect(status().isBadRequest());
        JsonNode ga = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Checkout.QUOTE_GUEST), guest(code, a[1], 1)))
                .andExpect(status().isOk()));
        assertEquals(10_000, ga.get("summary").get("voucherDiscount").asLong());
    }

    private Map<String, Object> guest(String code, long sku, int qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("recipientName", "Khách");
        m.put("phone", "0912345678");
        m.put("province", "Hồ Chí Minh");
        m.put("addressLine", "1 Lê Lợi");
        m.put("shippingMethod", "STANDARD");
        m.put("paymentMethod", "COD");
        if (code != null) {
            m.put("voucherCode", code);
        }
        m.put("items", List.of(Map.of("skuId", sku, "quantity", qty)));
        return m;
    }

    @Test
    void campaignVoucherWithAllTargetAndExcludedSkuAndLegacyVoucherUnchanged() throws Exception {
        admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        long[] a = product(t, "A", 100_000);
        long[] b = product(t, "B", 50_000);
        // target ALL nhưng loại trừ SKU B: giảm trên A, không giảm trên B
        String code = "SX" + t.toUpperCase();
        publishVoucher(code, Map.of("type", "ALL", "version", "1.0.0", "excludedSkuIds", List.of(String.valueOf(b[1]))), 1);
        assertEquals(10_000, discount(quote(cart(code, new long[]{a[1], 1}, new long[]{b[1], 2}))));
        JsonNode onlyB = quote(cart(code, new long[]{b[1], 2}));
        assertEquals(0, discount(onlyB));
        assertTrue(reason(onlyB).contains("VOUCHER_NO_ELIGIBLE_ITEMS"), reason(onlyB));

        // voucher cũ (tạo trực tiếp, không phạm vi): giảm trên cả giỏ như trước
        String legacy = "LG" + t.toUpperCase();
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Voucher.BASE), Map.of("code", legacy, "discountType", "PERCENT",
                "discountValue", 10, "minOrderValue", 0, "startsAt", Instant.now().minus(1, ChronoUnit.HOURS).toString(),
                "endsAt", Instant.now().plus(5, ChronoUnit.DAYS).toString(), "status", "PUBLISHED")).header("Authorization", admin))
                .andExpect(status().isCreated());
        assertEquals(20_000, discount(quote(cart(legacy, new long[]{a[1], 1}, new long[]{b[1], 2}))));
    }

    /**
     * Khuyến mãi sản phẩm (MARKDOWN) chọn đúng SKU A: SKU anh em A2 cùng sản phẩm KHÔNG được giảm ở tiền thật
     * (giỏ/báo giá/đơn), khớp với engine runtime của wizard (tính theo SKU).
     */
    @Test
    void markdownCampaignOnOneSkuDoesNotDiscountSiblingSku() throws Exception {
        admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        long[] a = product(t, "A", 100_000);
        // SKU anh em cùng sản phẩm A
        long a2 = body(mvc.perform(jsonRequest(post(CATALOG + "/" + a[0] + "/variants"), Map.of("skuCode", "VSA2-" + t,
                "listPrice", 100_000, "onHand", 50, "status", "PUBLISHED")).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        Instant now = Instant.now();
        String d = body(mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "MD " + t, "description", "x",
                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(), "endsAt", now.plus(7, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        String sid = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "Giảm 20%", "schemaType", "MARKDOWN",
                "costSources", List.of("ECOMMERCE"), "conditionMode", "IMPLICIT_ALWAYS")).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/benefit"), Map.of("configType", "MARKDOWN",
                "configVersion", "1.0.0", "values", Map.of("discountKind", "PERCENT", "discountValue", 20)))
                .header("Authorization", admin)).andExpect(status().isOk());
        String comp = DRAFTS + "/" + d;
        mvc.perform(jsonRequest(put(comp + "/target"), Map.of("config", Map.of("type", "MANUAL", "version", "1.0.0",
                "skuIds", List.of(String.valueOf(a[1])), "lineFilter", Map.of("discountState", "ANY")))).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(put(comp + "/audience"), Map.of("config", Map.of("type", "ALL", "version", "1.0.0"))).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(put(comp + "/applicability"), Map.of("config", Map.of("type", "CHANNEL_STORE_APPLICABILITY", "version", "1.0.0",
                "channels", List.of("ONLINE"), "stores", Map.of("type", "ALL", "version", "1.0.0")))).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(post(comp + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        // SKU được chọn: giảm 20%; SKU anh em: không giảm (cả báo giá guest storefront lẫn báo giá admin)
        assertEquals(20_000, guestDiscount(a[1]));
        assertEquals(0, guestDiscount(a2));
        assertEquals(20_000, quote(cart("KHONGCO", new long[]{a[1], 1})).get("pricing").get("discountTotal").asLong());
        assertEquals(0, quote(cart("KHONGCO", new long[]{a2, 1})).get("pricing").get("discountTotal").asLong());
        // giá hiển thị trên storefront theo SKU
        JsonNode detail = body(mvc.perform(get(V1 + UrlConstant.Product.BASE + "/vsa-" + t)).andExpect(status().isOk()));
        String all = detail.toString();
        assertTrue(all.contains("80000"), all);
    }

    private long guestDiscount(long sku) throws Exception {
        JsonNode q = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Checkout.QUOTE_GUEST), guest(null, sku, 1)))
                .andExpect(status().isOk()));
        return q.get("summary").get("productDiscount").asLong();
    }

    @Test
    void campaignVoucherWithEmptyTargetFailsPublish() throws Exception {
        admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        long[] a = product(t, "A", 100_000);
        // MANUAL chỉ gồm SKU đã bị loại trừ -> không còn SKU nào: phát hành bị chặn (không tạo voucher "không giới hạn")
        Instant now = Instant.now();
        String d = body(mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "VS empty " + t, "description", "x",
                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(), "endsAt", now.plus(7, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        String sid = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "V", "schemaType", "VOUCHER",
                "costSources", List.of("ECOMMERCE"))).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/condition"), Map.of("configType", "TARGET_SUBTOTAL",
                "configVersion", "1.0.0", "values", Map.of("operator", "GREATER_THAN_OR_EQUAL", "amountMinor", 1)))
                .header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/benefit"), Map.of("configType", "VOUCHER",
                "configVersion", "1.0.0", "values", Map.of("discountKind", "PERCENT", "discountValue", 10)))
                .header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid + "/voucher-codes"), Map.of("code", "SE" + t.toUpperCase(),
                "status", "ACTIVE", "visibilityDistributionPolicy", Map.of("type", "VOUCHER_VISIBILITY_DISTRIBUTION_POLICY", "version", "1.0.0",
                        "visibilityType", "PUBLIC", "distributionChannels", List.of()))).header("Authorization", admin)).andExpect(status().isOk());
        String comp = DRAFTS + "/" + d;
        mvc.perform(jsonRequest(put(comp + "/target"), Map.of("config", Map.of("type", "MANUAL", "version", "1.0.0",
                "skuIds", List.of(String.valueOf(a[1])), "excludedSkuIds", List.of(String.valueOf(a[1]))))).header("Authorization", admin));
        mvc.perform(jsonRequest(put(comp + "/audience"), Map.of("config", Map.of("type", "ALL", "version", "1.0.0"))).header("Authorization", admin));
        mvc.perform(jsonRequest(put(comp + "/applicability"), Map.of("config", Map.of("type", "CHANNEL_STORE_APPLICABILITY", "version", "1.0.0",
                "channels", List.of("ONLINE"), "stores", Map.of("type", "ALL", "version", "1.0.0")))).header("Authorization", admin));
        int st = mvc.perform(post(comp + "/publish").header("Authorization", admin)).andReturn().getResponse().getStatus();
        assertTrue(st == 400 || st == 409, "phát hành phải bị chặn, status=" + st);
    }
}

package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Vòng đời đơn: huỷ trả tồn + voucher, COD tự PAID khi giao, hoàn tiền không vượt tổng đơn. Dữ liệu tạo qua API. */
class OmsOrderLifecycleIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    private long productId;
    private long skuId;
    private String voucherCode;
    private String t;
    private String admin;
    private String customer;
    private long addressId;

    private void setUp() throws Exception {
        t = UUID.randomUUID().toString().substring(0, 8);
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 1_000_000, "onHand", 5,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        voucherCode = "VC" + t.toUpperCase();
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Voucher.BASE), Map.of("code", voucherCode, "discountType", "FIXED",
                        "discountValue", 100_000, "usageLimit", 5,
                        "startsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                        "endsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(), "status", "PUBLISHED"))
                        .header("Authorization", admin))
                .andExpect(status().is2xxSuccessful());

        customer = bearer(customerAccessToken());
        addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String placeOrder(boolean withVoucher) throws Exception {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("addressId", addressId, "shippingMethod", "STANDARD",
                "paymentMethod", "COD", "items", List.of(Map.of("skuId", skuId, "quantity", 2))));
        if (withVoucher) {
            req.put("voucherCode", voucherCode);
        }
        return body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), req).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();
    }

    private int availableStock() throws Exception {
        return body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("availableStock").asInt();
    }

    private int voucherUsed() throws Exception {
        for (JsonNode v : body(mvc.perform(get(V1 + UrlAdminConstant.Voucher.BASE).header("Authorization", admin)))) {
            if (voucherCode.equals(v.get("code").asText())) {
                return v.get("usedCount").asInt();
            }
        }
        throw new AssertionError("không thấy voucher " + voucherCode);
    }

    @Test
    void cancelReleasesStockAndVoucherExactlyOnce() throws Exception {
        setUp();
        org.junit.jupiter.api.Assertions.assertEquals(5, availableStock());
        String code = placeOrder(true);
        org.junit.jupiter.api.Assertions.assertEquals(3, availableStock());
        org.junit.jupiter.api.Assertions.assertEquals(1, voucherUsed());

        mvc.perform(post(ORDERS + "/" + code + "/cancel").header("Authorization", admin)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(5, availableStock());
        org.junit.jupiter.api.Assertions.assertEquals(0, voucherUsed());

        // Huỷ lần hai bị từ chối và KHÔNG nhả thêm.
        mvc.perform(post(ORDERS + "/" + code + "/cancel").header("Authorization", admin)).andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(5, availableStock());
        org.junit.jupiter.api.Assertions.assertEquals(0, voucherUsed());

        // Huỷ qua PATCH status cũng nhả (đơn mới, không voucher): tồn trở lại 5.
        String code2 = placeOrder(false);
        org.junit.jupiter.api.Assertions.assertEquals(3, availableStock());
        mvc.perform(jsonRequest(patch(ORDERS + "/" + code2 + "/status"), Map.of("status", "CANCELLED")).header("Authorization", admin))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(5, availableStock());
    }

    @Test
    void codOrderBecomesPaidWhenDeliveredAndRefundIsCapped() throws Exception {
        setUp();
        String code = placeOrder(false);
        mvc.perform(get(V1 + UrlConstant.Order.BASE + "/" + code).header("Authorization", customer))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"));
        for (String next : List.of("CONFIRMED", "SHIPPING")) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", next)).header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"));
        }
        mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", "DELIVERED")).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        // Quy tắc hoàn tiền: hoàn một phần -> paymentStatus vẫn PAID (refundedAmount cộng dồn); hoàn đủ tổng đơn -> REFUNDED.
        // Hoàn vượt tổng đơn -> 400.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 999_999_999, "reason", "thử"))
                        .header("Authorization", admin)).andExpect(status().isBadRequest());
        JsonNode partial = body(mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 1_000, "reason", "thử"))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID")));
        assertEquals(1_000, partial.get("refundedAmount").asLong());
        long remaining = partial.get("grandTotal").asLong() - 1_000;
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", remaining + 1, "reason", "quá"))
                        .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", remaining, "reason", "hoàn đủ"))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));
        // Đã REFUNDED thì không hoàn thêm được nữa.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 1, "reason", "thừa"))
                        .header("Authorization", admin)).andExpect(status().isConflict());
    }
}

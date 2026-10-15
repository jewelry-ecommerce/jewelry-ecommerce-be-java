package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Đồng thời: đặt đơn không làm âm tồn; huỷ nhiều lần chỉ nhả tồn/voucher một lần; hoàn tiền cộng dồn không vượt tổng đơn. */
class OmsConcurrencyIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    private long productId;
    private long skuId;
    private String admin;
    private String customer;
    private long addressId;
    private String voucherCode;

    private void setUp(int onHand) throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 1_000_000, "onHand", onHand,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        voucherCode = "CC" + t.toUpperCase();
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Voucher.BASE), Map.of("code", voucherCode, "discountType", "FIXED",
                        "discountValue", 100_000, "usageLimit", 50,
                        "startsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                        "endsAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(), "status", "PUBLISHED"))
                        .header("Authorization", admin)).andExpect(status().isCreated());
        customer = bearer(customerAccessToken());
        addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
    }

    private int available() throws Exception {
        return body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("availableStock").asInt();
    }

    private int voucherUsed() throws Exception {
        for (JsonNode v : body(mvc.perform(get(V1 + UrlAdminConstant.Voucher.BASE).header("Authorization", admin)))) {
            if (voucherCode.equals(v.get("code").asText())) {
                return v.get("usedCount").asInt();
            }
        }
        throw new AssertionError("không thấy voucher");
    }

    private String placeOrder(boolean withVoucher) throws Exception {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("addressId", addressId, "shippingMethod", "STANDARD",
                "paymentMethod", "COD", "items", List.of(Map.of("skuId", skuId, "quantity", 1))));
        if (withVoucher) {
            req.put("voucherCode", voucherCode);
        }
        return body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), req).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();
    }

    private <T> List<T> parallel(int n, Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<T>> calls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                calls.add(call);
            }
            List<T> out = new ArrayList<>();
            for (Future<T> f : pool.invokeAll(calls)) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentOrdersNeverOversellStock() throws Exception {
        setUp(3);
        List<Integer> statuses = parallel(8, () -> mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE),
                Map.of("addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", 1)))).header("Authorization", customer))
                .andReturn().getResponse().getStatus());
        long created = statuses.stream().filter(s -> s == 201).count();
        assertEquals(3, created, "chỉ bán đúng số tồn: " + statuses);
        assertTrue(statuses.stream().filter(s -> s != 201).allMatch(s -> s >= 400 && s < 500), "đơn thất bại phải là 4xx: " + statuses);
        assertEquals(0, available(), "tồn khả dụng không âm và bằng 0");
    }

    @Test
    void concurrentCancelReleasesStockAndVoucherOnce() throws Exception {
        setUp(5);
        String code = placeOrder(true);
        assertEquals(4, available());
        assertEquals(1, voucherUsed());
        List<Integer> statuses = parallel(6, () -> mvc.perform(post(ORDERS + "/" + code + "/cancel").header("Authorization", admin))
                .andReturn().getResponse().getStatus());
        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "đúng một lần huỷ thành công: " + statuses);
        assertTrue(statuses.stream().filter(s -> s != 200).allMatch(s -> s >= 400 && s < 500), "các lần còn lại là 4xx: " + statuses);
        assertEquals(5, available(), "nhả tồn đúng một lần");
        assertEquals(0, voucherUsed(), "nhả voucher đúng một lần");
    }

    @Test
    void concurrentRefundsNeverExceedGrandTotal() throws Exception {
        setUp(5);
        String code = placeOrder(false);
        for (String next : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", next)).header("Authorization", admin))
                    .andExpect(status().isOk());
        }
        long total = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin))).get("grandTotal").asLong();
        long each = total * 6 / 10; // hai lần hoàn 60% sẽ vượt tổng đơn
        List<Integer> statuses = parallel(5, () -> mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"),
                Map.of("amount", each, "reason", "đồng thời")).header("Authorization", admin)).andReturn().getResponse().getStatus());
        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "chỉ một lần hoàn 60% thành công: " + statuses);
        JsonNode after = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)));
        assertEquals(each, after.get("refundedAmount").asLong());
        assertTrue(after.get("refundedAmount").asLong() <= total);
    }
}

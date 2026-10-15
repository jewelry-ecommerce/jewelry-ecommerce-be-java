package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Idempotency-Key đặt đơn: gửi lại không tạo đơn/trừ tồn thêm; khác nội dung -> 409; sai định dạng -> 400; đồng thời -> 1 đơn. */
class OrderIdempotencyIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;

    private long productId;
    private long skuId;
    private String admin;
    private String customer;
    private long addressId;

    private void setUp() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 1_000_000, "onHand", 10,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        customer = bearer(customerAccessToken());
        addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
    }

    private int available() throws Exception {
        return body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("availableStock").asInt();
    }

    private Map<String, Object> req(int qty) {
        return Map.of("addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                "items", List.of(Map.of("skuId", skuId, "quantity", qty)));
    }

    private org.springframework.test.web.servlet.ResultActions place(Map<String, Object> body, String key) throws Exception {
        var b = jsonRequest(post(V1 + UrlConstant.Order.BASE), body).header("Authorization", customer);
        if (key != null) {
            b = b.header("Idempotency-Key", key);
        }
        return mvc.perform(b);
    }

    @Test
    void sameKeySamePayloadReturnsSameOrderWithoutSecondReservation() throws Exception {
        setUp();
        String key = "key-" + UUID.randomUUID();
        String first = body(place(req(2), key).andExpect(status().isCreated())).get("code").asText();
        assertEquals(8, available());
        String second = body(place(req(2), key).andExpect(status().isCreated())).get("code").asText();
        assertEquals(first, second, "gửi lại cùng khoá phải trả đúng đơn cũ");
        assertEquals(8, available(), "gửi lại không được giữ chỗ tồn thêm");

        // Không có khoá -> mỗi lần là một đơn mới (hành vi cũ giữ nguyên).
        String a = body(place(req(1), null).andExpect(status().isCreated())).get("code").asText();
        String b = body(place(req(1), null).andExpect(status().isCreated())).get("code").asText();
        assertTrue(!a.equals(b));
        assertEquals(6, available());
    }

    @Test
    void sameKeyDifferentPayloadIsConflictAndBadKeyIsRejected() throws Exception {
        setUp();
        String key = "key-" + UUID.randomUUID();
        place(req(1), key).andExpect(status().isCreated());
        place(req(3), key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(9, available(), "đơn bị từ chối không được giữ chỗ tồn");
        place(req(1), "ngan").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("IDEMPOTENCY_KEY_INVALID"));
        place(req(1), "co dau cach khong hop le!").andExpect(status().isBadRequest());
        assertEquals(9, available());
    }

    @Test
    void guestOrderReplayAndOwnerIsolation() throws Exception {
        setUp();
        String key = "guest-" + UUID.randomUUID();
        Map<String, Object> guest = Map.of("recipientName", "Khách", "phone", "0912345678", "province", "HCM",
                "district", "Q1", "ward", "P1", "addressLine", "2 Lê Lợi", "shippingMethod", "STANDARD", "paymentMethod", "COD",
                "items", List.of(Map.of("skuId", skuId, "quantity", 1)));
        String c1 = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.GUEST), guest).header("Idempotency-Key", key))
                .andExpect(status().isCreated())).get("code").asText();
        String c2 = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.GUEST), guest).header("Idempotency-Key", key))
                .andExpect(status().isCreated())).get("code").asText();
        assertEquals(c1, c2);
        assertEquals(9, available());
        // Cùng khoá nhưng là khách đăng nhập khác (owner khác) -> không nhận đơn của khách vãng lai.
        String c3 = body(place(req(1), key).andExpect(status().isCreated())).get("code").asText();
        assertTrue(!c3.equals(c1));
    }

    @Test
    void concurrentRequestsWithSameKeyCreateExactlyOneOrder() throws Exception {
        setUp();
        String key = "race-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> {
                    var r = place(req(2), key).andReturn().getResponse();
                    return r.getStatus() + ":" + (r.getStatus() == 201
                            ? json.readTree(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data").get("code").asText() : "");
                });
            }
            Set<String> codes = new HashSet<>();
            for (Future<String> f : pool.invokeAll(calls)) {
                String res = f.get();
                assertTrue(res.startsWith("201:"), "mọi request cùng khoá đều phải nhận 201 + cùng đơn, nhận " + res);
                codes.add(res);
            }
            assertEquals(1, codes.size(), "phải chỉ có một đơn: " + codes);
            assertEquals(8, available(), "tồn chỉ bị giữ một lần");
        } finally {
            pool.shutdownNow();
        }
    }
}

package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Giao hàng chốt xuất tồn đúng một lần, trả hàng theo số lượng, hoàn tiền từng phần, huỷ đồng thời. Dữ liệu tạo qua API. */
class OmsFulfillmentReturnRefundIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    private String t;
    private String skuCode;
    private long skuId;
    private String admin;
    private String customer;
    private long addressId;

    private void setUp() throws Exception {
        t = UUID.randomUUID().toString().substring(0, 8);
        skuCode = "SKU-" + t;
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", skuCode, "listPrice", 1_000_000, "onHand", 5,
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

    private String placeCodOrder(int qty) throws Exception {
        return body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", addressId,
                        "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", qty)))).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();
    }

    private JsonNode stock() throws Exception {
        for (JsonNode s : body(mvc.perform(get(V1 + UrlAdminConstant.Inventory.STOCK_LEVELS).param("search", skuCode)
                .header("Authorization", admin)).andExpect(status().isOk()))) {
            if (skuCode.equals(s.get("skuCode").asText())) {
                return s;
            }
        }
        throw new AssertionError("không thấy " + skuCode);
    }

    private void moveTo(String code, String... statuses) throws Exception {
        for (String st : statuses) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", st)).header("Authorization", admin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void deliveryConsumesStockExactlyOnceAndCodBecomesPaid() throws Exception {
        setUp();
        String code = placeCodOrder(2);
        assertEquals(5, stock().get("onHand").asInt());
        assertEquals(2, stock().get("reserved").asInt());

        moveTo(code, "CONFIRMED", "SHIPPING");
        assertEquals(5, stock().get("onHand").asInt(), "chưa giao thì chưa trừ tồn vật lý");
        mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", "DELIVERED")).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
        assertEquals(3, stock().get("onHand").asInt());
        assertEquals(0, stock().get("reserved").asInt());

        // Giao lần hai bị từ chối và không trừ thêm.
        mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", "DELIVERED")).header("Authorization", admin))
                .andExpect(status().isConflict());
        assertEquals(3, stock().get("onHand").asInt());
        assertEquals(0, stock().get("reserved").asInt());
    }

    @Test
    void partialReturnRestocksAndOnlyFullReturnMarksOrderReturned() throws Exception {
        setUp();
        String code = placeCodOrder(3);
        moveTo(code, "CONFIRMED", "SHIPPING", "DELIVERED");
        assertEquals(2, stock().get("onHand").asInt());
        long lineId = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin))).get("items").get(0).get("id").asLong();

        // Trả 1/3 -> đơn vẫn DELIVERED, tồn +1, dòng ghi returnedQuantity=1.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/return"), Map.of("lines", List.of(Map.of("lineId", lineId, "quantity", 1))))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.items[0].returnedQuantity").value(1));
        assertEquals(3, stock().get("onHand").asInt());

        // Trả vượt số còn lại (chỉ còn 2) -> 400, tồn không đổi.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/return"), Map.of("lines", List.of(Map.of("lineId", lineId, "quantity", 3))))
                        .header("Authorization", admin)).andExpect(status().isBadRequest());
        assertEquals(3, stock().get("onHand").asInt());

        // Trả nốt 2 (cách cũ: lineIds = toàn bộ phần còn lại) -> RETURNED, tồn về 5.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/return"), Map.of("lineIds", List.of(lineId))).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.items[0].returnedQuantity").value(3));
        assertEquals(5, stock().get("onHand").asInt());
        assertEquals(0, stock().get("reserved").asInt());

        // Trả lại lần nữa -> 409.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/return"), Map.of("lineIds", List.of(lineId))).header("Authorization", admin))
                .andExpect(status().isConflict());
        assertEquals(5, stock().get("onHand").asInt());
    }

    @Test
    void partialRefundsAccumulateAndAreCappedByOrderTotal() throws Exception {
        setUp();
        String code = placeCodOrder(2); // grandTotal 2.000.000
        moveTo(code, "CONFIRMED", "SHIPPING", "DELIVERED"); // COD -> PAID

        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 500_000, "reason", "Lỗi nhỏ"))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.refundedAmount").value(500_000))
                .andExpect(jsonPath("$.data.refundableAmount").value(1_500_000));

        // Vượt số còn hoàn / số tiền không dương -> 400; dữ liệu không đổi.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 1_600_000, "reason", "x"))
                        .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 0, "reason", "x"))
                        .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin))
                .andExpect(jsonPath("$.data.refundedAmount").value(500_000));

        // Hoàn nốt phần còn lại -> REFUNDED; hoàn thêm -> 409.
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 1_500_000, "reason", "Hoàn đủ"))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundableAmount").value(0));
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/refund"), Map.of("amount", 1, "reason", "x"))
                        .header("Authorization", admin)).andExpect(status().isConflict());
    }

    @Test
    void concurrentCancelsReleaseStockOnlyOnce() throws Exception {
        setUp();
        String code = placeCodOrder(2);
        assertEquals(2, stock().get("reserved").asInt());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> cancel = () -> mvc.perform(post(ORDERS + "/" + code + "/cancel").header("Authorization", admin))
                    .andReturn().getResponse().getStatus();
            Future<Integer> a = pool.submit(cancel);
            Future<Integer> b = pool.submit(cancel);
            int ok = 0;
            for (int s : new int[] {a.get(), b.get()}) {
                if (s == 200) {
                    ok++;
                }
            }
            assertEquals(1, ok, "đúng một lần huỷ thành công");
        } finally {
            pool.shutdown();
        }
        assertEquals(0, stock().get("reserved").asInt(), "nhả tồn đúng một lần, không âm/không nhả đôi");
        assertEquals(5, stock().get("onHand").asInt());
    }
}

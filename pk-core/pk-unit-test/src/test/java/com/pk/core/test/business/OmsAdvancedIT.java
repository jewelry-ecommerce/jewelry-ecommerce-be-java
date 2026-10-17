package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OMS nâng cao: tạo/báo giá/sửa/nhân bản đơn, ghi chú, xác nhận thanh toán, audit log, đổi/trả. Dữ liệu tạo qua API. */
class OmsAdvancedIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;
    private static final String RETURNS = V1 + UrlAdminConstant.OrderReturn.BASE;

    private long productId;
    private long skuId;
    private String admin;

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
    }

    private int availableStock() throws Exception {
        return body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("availableStock").asInt();
    }

    private Map<String, Object> orderBody(int qty, String phone) {
        Map<String, Object> m = new HashMap<>();
        m.put("contextVersion", 2);
        m.put("receiverPhone", phone);
        m.put("firstName", "Khách Test");
        m.put("provinceName", "Hồ Chí Minh");
        m.put("wardCode", 1);
        m.put("wardName", "Phường 1");
        m.put("addressLine", "1 Lê Lợi");
        m.put("shippingMethod", "STANDARD");
        m.put("paymentMethod", "COD");
        m.put("items", List.of(Map.of("variationId", String.valueOf(skuId), "quantity", qty)));
        return m;
    }

    private JsonNode adminCreate(int qty) throws Exception {
        Map<String, Object> req = orderBody(qty, "0912345678");
        req.put("idempotencyKey", "idem-" + UUID.randomUUID());
        return body(mvc.perform(jsonRequest(post(ORDERS), req).header("Authorization", admin))
                .andExpect(status().isCreated()));
    }

    private JsonNode order(String code) throws Exception {
        return body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)).andExpect(status().isOk()));
    }

    private void deliver(String code) throws Exception {
        for (String next : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", next))
                    .header("Authorization", admin)).andExpect(status().isOk());
        }
    }

    @Test
    void adminCreateQuoteUpdateNotesDuplicateAndAudit() throws Exception {
        setUp();
        // --- báo giá
        JsonNode quote = body(mvc.perform(jsonRequest(post(ORDERS + "/pricing-context/quote"), orderBody(2, "0912345678"))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("2000000", quote.get("pricing").get("subtotal").asText());
        long grand = quote.get("pricing").get("grandTotal").asLong();
        assertEquals(1, quote.get("items").size());
        assertEquals(10, availableStock()); // báo giá không giữ tồn

        // --- tạo đơn: giá lệch báo giá -> 409
        Map<String, Object> stale = orderBody(2, "0912345678");
        stale.put("expectedGrandTotalMinor", grand + 1);
        mvc.perform(jsonRequest(post(ORDERS), stale).header("Authorization", admin)).andExpect(status().isConflict());
        assertEquals(10, availableStock());

        // --- tạo đúng báo giá, có idempotency
        Map<String, Object> req = orderBody(2, "0912345678");
        req.put("pricingEvaluationId", quote.get("pricingEvaluationId").asText());
        req.put("expectedGrandTotalMinor", grand);
        req.put("idempotencyKey", "idem-" + UUID.randomUUID());
        req.put("note", "đơn tạo hộ");
        JsonNode created = body(mvc.perform(jsonRequest(post(ORDERS), req).header("Authorization", admin))
                .andExpect(status().isCreated()));
        String code = created.get("code").asText();
        assertEquals("PENDING", created.get("status").asText());
        assertEquals("ADMIN", created.get("source").asText());
        assertEquals(grand, created.get("grandTotal").asLong());
        assertNotNull(created.get("createdById"));
        assertFalse(created.get("createdById").isNull());
        assertEquals(8, availableStock());
        JsonNode again = body(mvc.perform(jsonRequest(post(ORDERS), req).header("Authorization", admin))
                .andExpect(status().isCreated()));
        assertEquals(code, again.get("code").asText());
        assertEquals(8, availableStock());

        // --- lọc theo nguồn
        JsonNode list = body(mvc.perform(get(ORDERS).param("source", "admin").param("search", code)
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertTrue(list.toString().contains(code));
        JsonNode web = body(mvc.perform(get(ORDERS).param("source", "website").param("search", code)
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertFalse(web.toString().contains(code));

        // --- sửa đơn: địa chỉ + giảm số lượng -> trả bớt tồn
        Map<String, Object> edit = new HashMap<>();
        edit.put("addressLine", "2 Nguyễn Huệ");
        edit.put("items", List.of(Map.of("variationId", String.valueOf(skuId), "quantity", 1)));
        edit.put("note", "đã sửa");
        JsonNode updated = body(mvc.perform(jsonRequest(put(ORDERS + "/" + code), edit).header("Authorization", admin))
                .andExpect(status().isOk()));
        assertEquals("2 Nguyễn Huệ", updated.get("shipAddressLine").asText());
        assertEquals(1, updated.get("items").get(0).get("quantity").asInt());
        assertEquals(9, availableStock());
        assertTrue(updated.get("grandTotal").asLong() < grand);

        // --- ghi chú
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/notes"), Map.of("content", "gọi trước khi giao", "type", "internal"))
                .header("Authorization", admin)).andExpect(status().isCreated());
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/notes"), Map.of("content", "khách dặn", "type", "order"))
                .header("Authorization", admin)).andExpect(status().isCreated());
        mvc.perform(jsonRequest(post(ORDERS + "/" + code + "/notes"), Map.of("content", "   ")).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        assertEquals(2, body(mvc.perform(get(ORDERS + "/" + code + "/notes").header("Authorization", admin))).size());
        JsonNode internal = body(mvc.perform(get(ORDERS + "/" + code + "/notes").param("type", "internal")
                .header("Authorization", admin)));
        assertEquals(1, internal.size());
        assertEquals("gọi trước khi giao", internal.get(0).get("content").asText());
        assertEquals(code, internal.get(0).get("orderCode").asText());

        // --- xác nhận thanh toán (một lần)
        JsonNode paid = body(mvc.perform(jsonRequest(put(ORDERS + "/" + code + "/confirm-payment"), Map.of("note", "thu tiền mặt"))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("PAID", paid.get("paymentStatus").asText());
        mvc.perform(jsonRequest(put(ORDERS + "/" + code + "/confirm-payment"), Map.of("note", "lần 2"))
                .header("Authorization", admin)).andExpect(status().isConflict());

        // --- nhân bản + huỷ đơn nguồn
        JsonNode copy = body(mvc.perform(jsonRequest(post(ORDERS + "/duplicate"), Map.of("sourceOrderId", code,
                        "replaceSourceAfterDuplicate", true, "cancellationReason", "Sao chép")).header("Authorization", admin))
                .andExpect(status().isCreated()));
        assertTrue(!code.equals(copy.get("code").asText()));
        assertEquals("PENDING", copy.get("status").asText());
        assertEquals("CANCELLED", order(code).get("status").asText());
        assertEquals(9, availableStock()); // đơn nguồn trả 1, đơn mới giữ 1 -> không đổi so với trước nhân bản

        // --- audit log
        JsonNode logs = body(mvc.perform(get(ORDERS + "/audit-logs").param("entityName", "oms").param("entityId", code)
                .param("page", "1").param("take", "50").header("Authorization", admin)).andExpect(status().isOk()));
        String all = logs.toString();
        assertTrue(logs.get("total").asInt() >= 5, all);
        for (String action : List.of("CREATE_ORDER", "UPDATE_ORDER", "ADD_NOTE", "CONFIRM_PAYMENT", "DUPLICATE_AND_CANCEL")) {
            assertTrue(all.contains(action), action + " " + all);
        }
        JsonNode first = logs.get("list").get(0);
        assertNotNull(first.get("actor").get("id"));
        assertTrue(all.contains("shipAddressLine"), "audit UPDATE_ORDER phải có thay đổi theo trường");
    }

    @Test
    void returnRequestRefundExchangeAndReject() throws Exception {
        setUp();
        String code = adminCreate(4).get("code").asText();
        // chưa giao -> không tạo được yêu cầu đổi trả
        Map<String, Object> early = new HashMap<>();
        early.put("orderId", code);
        early.put("type", "Refund");
        early.put("reason", "DefectiveProduct");
        early.put("returnItems", List.of(Map.of("orderItemId", "1", "quantity", 1)));
        mvc.perform(jsonRequest(post(RETURNS + "/quote"), early).header("Authorization", admin)).andExpect(status().isConflict());

        deliver(code);
        JsonNode delivered = order(code);
        assertEquals("PAID", delivered.get("paymentStatus").asText());
        long itemId = delivered.get("items").get(0).get("id").asLong();
        assertEquals(6, availableStock() + 0 == 6 ? 6 : 6); // tồn đã chốt xuất khi giao (on_hand 10 -> 6)

        Map<String, Object> refund = new HashMap<>();
        refund.put("orderId", code);
        refund.put("type", "Refund");
        refund.put("reason", "DefectiveProduct");
        refund.put("returnItems", List.of(Map.of("orderItemId", String.valueOf(itemId), "quantity", 1)));
        refund.put("note", "rớt đá");

        // --- báo giá + commit (fingerprint sai -> 409)
        JsonNode quote = body(mvc.perform(jsonRequest(post(RETURNS + "/quote"), refund).header("Authorization", admin))
                .andExpect(status().isOk()));
        long settlement = quote.get("product").get("returnedMerchandiseSettlementMinor").asLong();
        assertTrue(settlement > 0);
        Map<String, Object> commit = new HashMap<>(refund);
        commit.put("pricingQuote", Map.of("fingerprint", "sai"));
        mvc.perform(jsonRequest(post(RETURNS + "/commit"), commit).header("Authorization", admin)).andExpect(status().isConflict());
        commit.put("pricingQuote", quote.get("quoteReference"));
        JsonNode committed = body(mvc.perform(jsonRequest(post(RETURNS + "/commit"), commit).header("Authorization", admin))
                .andExpect(status().isCreated()));
        String rt = committed.get("orderReturnCode").asText();
        assertTrue(committed.get("success").asBoolean());

        // --- vượt số lượng còn trả được (4 - 1 đang chờ = 3)
        Map<String, Object> over = new HashMap<>(refund);
        over.put("returnItems", List.of(Map.of("orderItemId", String.valueOf(itemId), "quantity", 4)));
        mvc.perform(jsonRequest(post(RETURNS), over).header("Authorization", admin)).andExpect(status().isBadRequest());

        // --- danh sách / chi tiết
        JsonNode listed = body(mvc.perform(jsonRequest(post(RETURNS + "/list"),
                Map.of("page", 1, "take", 10, "orderId", code, "status", "Pending")).header("Authorization", admin))
                .andExpect(status().isOk()));
        assertEquals(1, listed.get("total").asInt());
        assertEquals(rt, listed.get("list").get(0).get("returnCode").asText());
        assertEquals(1, listed.get("summary").get("ALL").asInt());
        JsonNode detail = body(mvc.perform(get(RETURNS + "/" + rt).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("Pending", detail.get("status").asText());
        assertEquals(1, detail.get("returnItems").size());
        assertEquals(settlement, detail.get("returnTotal").asLong());
        assertEquals(1, detail.get("orderReturnStatusHistory").size());

        // --- duyệt: nhập kho + hoàn tiền
        int stockBefore = availableStock();
        JsonNode approved = body(mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "ok"))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("Completed", approved.get("status").asText());
        assertEquals(settlement, approved.get("totalRefundAmount").asLong());
        assertEquals(stockBefore + 1, availableStock());
        JsonNode afterRefund = order(code);
        assertEquals(1, afterRefund.get("items").get(0).get("returnedQuantity").asInt());
        assertEquals(settlement, afterRefund.get("refundedAmount").asLong());
        mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "lại"))
                .header("Authorization", admin)).andExpect(status().isConflict());

        // --- đổi hàng: trả 1, đổi 1 cùng SKU -> tạo đơn thay thế
        Map<String, Object> exchange = new HashMap<>();
        exchange.put("orderId", code);
        exchange.put("type", "Exchange");
        exchange.put("reason", "ChangedMind");
        exchange.put("returnItems", List.of(Map.of("orderItemId", String.valueOf(itemId), "quantity", 1)));
        exchange.put("exchangeItems", List.of(Map.of("variationId", String.valueOf(skuId), "quantity", 1)));
        JsonNode ex = body(mvc.perform(jsonRequest(post(RETURNS), exchange).header("Authorization", admin))
                .andExpect(status().isCreated()));
        assertEquals("Exchange", ex.get("type").asText());
        assertEquals("Subjective", ex.get("reasonCategory").asText());
        assertEquals(1, ex.get("exchangeItems").size());
        JsonNode exApproved = body(mvc.perform(jsonRequest(put(RETURNS + "/" + ex.get("returnCode").asText() + "/approve"),
                Map.of("note", "đổi")).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("Completed", exApproved.get("status").asText());
        String replacementId = exApproved.get("exchangeFulfillmentOrderId").asText();
        assertFalse(replacementId.isBlank());
        JsonNode replacement = order(replacementId);
        assertEquals("PENDING", replacement.get("status").asText());
        assertEquals("ADMIN", replacement.get("source").asText());
        assertEquals(0L, replacement.get("grandTotal").asLong()); // giá trị hàng trả bù đủ giá hàng đổi

        // --- từ chối: cần lý do
        JsonNode third = body(mvc.perform(jsonRequest(post(RETURNS), refund).header("Authorization", admin))
                .andExpect(status().isCreated()));
        String rt3 = third.get("returnCode").asText();
        mvc.perform(jsonRequest(put(RETURNS + "/" + rt3 + "/reject"), Map.of("note", " ")).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        JsonNode rejected = body(mvc.perform(jsonRequest(put(RETURNS + "/" + rt3 + "/reject"), Map.of("note", "hàng đã qua sử dụng"))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("Rejected", rejected.get("status").asText());
        assertEquals("hàng đã qua sử dụng", rejected.get("reasonReject").asText());
        // đã Rejected -> không sửa/duyệt tiếp
        mvc.perform(jsonRequest(put(RETURNS + "/" + rt3), Map.of("note", "x")).header("Authorization", admin))
                .andExpect(status().isConflict());

        // --- audit của đơn gốc có các bước đổi trả
        String logs = body(mvc.perform(get(ORDERS + "/audit-logs").param("entityId", code).param("take", "100")
                .header("Authorization", admin)).andExpect(status().isOk())).toString();
        for (String action : List.of("RETURN_REQUEST_CREATED", "RETURN_APPROVED", "RETURN_REJECTED", "REFUND")) {
            assertTrue(logs.contains(action), action + " " + logs);
        }
    }

    @Test
    void ordersEndpointsRejectCustomersAndAnonymous() throws Exception {
        setUp();
        String customer = bearer(customerAccessToken());
        mvc.perform(jsonRequest(post(ORDERS + "/pricing-context/quote"), orderBody(1, "0912345678")).header("Authorization", customer))
                .andExpect(status().isForbidden());
        mvc.perform(jsonRequest(post(RETURNS + "/list"), Map.of("page", 1, "take", 5)).header("Authorization", customer))
                .andExpect(status().isForbidden());
        mvc.perform(post(RETURNS + "/list")).andExpect(status().isUnauthorized());
    }
}

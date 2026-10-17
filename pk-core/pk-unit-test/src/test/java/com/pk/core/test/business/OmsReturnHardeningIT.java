package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy 3 lỗi FE v7 báo ở luồng đổi/trả: (1) duyệt song song chạy tác dụng phụ 2 lần, (2) giá hàng đổi lệch giữa
 * số hoàn / chênh lệch / đơn thay thế, (3) báo giá lại yêu cầu đang sửa (returnId).
 */
class OmsReturnHardeningIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;
    private static final String RETURNS = V1 + UrlAdminConstant.OrderReturn.BASE;

    private long productId;
    private long skuId;
    private String skuCode;
    private String admin;

    private void setUp() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        skuCode = "SKU-" + t;
        productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                "variants", List.of(Map.of("skuCode", skuCode, "listPrice", 1_000_000, "onHand", 20,
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

    private void setListPrice(long price) throws Exception {
        Map<String, Object> upd = new HashMap<>();
        upd.put("skuCode", skuCode);
        upd.put("listPrice", price);
        mvc.perform(jsonRequest(put(CATALOG + "/" + productId + "/variants/" + skuId), upd).header("Authorization", admin))
                .andExpect(status().isOk());
    }

    private JsonNode order(String code) throws Exception {
        return body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)).andExpect(status().isOk()));
    }

    /** Tạo đơn qty cái, giao hàng; trả id dòng hàng và mã đơn. */
    private String[] deliveredOrder(int qty) throws Exception {
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
        m.put("items", List.of(Map.of("variationId", String.valueOf(skuId), "quantity", qty)));
        m.put("idempotencyKey", "idem-" + UUID.randomUUID());
        String code = body(mvc.perform(jsonRequest(post(ORDERS), m).header("Authorization", admin))
                .andExpect(status().isCreated())).get("code").asText();
        for (String next : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", next))
                    .header("Authorization", admin)).andExpect(status().isOk());
        }
        return new String[]{code, order(code).get("items").get(0).get("id").asText()};
    }

    private Map<String, Object> returnBody(String code, String itemId, int qty, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderId", code);
        m.put("type", type);
        m.put("reason", "DefectiveProduct");
        m.put("returnItems", List.of(Map.of("orderItemId", itemId, "quantity", qty)));
        if ("Exchange".equals(type)) {
            m.put("exchangeItems", List.of(Map.of("variationId", String.valueOf(skuId), "quantity", 1)));
        }
        return m;
    }

    @Test
    void concurrentApproveRunsSideEffectsExactlyOnce() throws Exception {
        setUp();
        String[] o = deliveredOrder(4);
        JsonNode created = body(mvc.perform(jsonRequest(post(RETURNS), returnBody(o[0], o[1], 1, "Refund"))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        String rt = created.get("returnCode").asText();
        long settlement = created.get("returnTotal").asLong();
        int stockBefore = availableStock();

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> job = () -> {
                go.await();
                return mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "song song"))
                        .header("Authorization", admin)).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(job));
        }
        go.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Integer> f : results) {
            int st = f.get();
            if (st == 200) {
                ok++;
            } else if (st == 409) {
                conflict++;
            }
        }
        pool.shutdown();
        assertEquals(1, ok, "đúng 1 request duyệt thành công");
        assertEquals(threads - 1, conflict, "các request còn lại nhận 409");
        assertEquals(stockBefore + 1, availableStock(), "nhập kho đúng 1 lần");
        JsonNode after = order(o[0]);
        assertEquals(1, after.get("items").get(0).get("returnedQuantity").asInt());
        assertEquals(settlement, after.get("refundedAmount").asLong(), "hoàn tiền đúng 1 lần");
        String logs = body(mvc.perform(get(ORDERS + "/audit-logs").param("entityId", o[0]).param("take", "100")
                .header("Authorization", admin)).andExpect(status().isOk())).toString();
        assertEquals(1, logs.split("RETURN_APPROVED", -1).length - 1, "audit duyệt đúng 1 lần");
    }

    @Test
    void approveVersusRejectAndCancelOnlyOneWins() throws Exception {
        setUp();
        String[] o = deliveredOrder(4);
        String rt = body(mvc.perform(jsonRequest(post(RETURNS), returnBody(o[0], o[1], 1, "Refund"))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("returnCode").asText();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> a = pool.submit(() -> {
            go.await();
            return mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "ok"))
                    .header("Authorization", admin)).andReturn().getResponse().getStatus();
        });
        Future<Integer> r = pool.submit(() -> {
            go.await();
            return mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/reject"), Map.of("note", "từ chối"))
                    .header("Authorization", admin)).andReturn().getResponse().getStatus();
        });
        Future<Integer> c = pool.submit(() -> {
            go.await();
            return mvc.perform(jsonRequest(put(RETURNS + "/" + rt), Map.of("status", "Cancelled"))
                    .header("Authorization", admin)).andReturn().getResponse().getStatus();
        });
        go.countDown();
        int wins = 0;
        for (int st : List.of(a.get(), r.get(), c.get())) {
            if (st == 200) {
                wins++;
            } else {
                assertEquals(409, st);
            }
        }
        pool.shutdown();
        assertEquals(1, wins, "chỉ một trong duyệt / từ chối / huỷ thành công");
        JsonNode detail = body(mvc.perform(get(RETURNS + "/" + rt).header("Authorization", admin)).andExpect(status().isOk()));
        String finalStatus = detail.get("status").asText();
        assertTrue(List.of("Completed", "Rejected", "Cancelled").contains(finalStatus), finalStatus);
        // lịch sử chỉ có đúng các bước của người thắng
        int refunded = order(o[0]).get("refundedAmount").asInt();
        assertEquals("Completed".equals(finalStatus), refunded > 0);
    }

    @Test
    void exchangePriceChangeAfterQuoteIsStaleThenRequoteSettlesConsistently() throws Exception {
        setUp();
        String[] o = deliveredOrder(4);
        JsonNode created = body(mvc.perform(jsonRequest(post(RETURNS), returnBody(o[0], o[1], 1, "Exchange"))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        String rt = created.get("returnCode").asText();
        long returnTotal = created.get("returnTotal").asLong();
        assertEquals(1_000_000L, created.get("exchangeTotal").asLong());
        int stockBefore = availableStock();

        // Giá hàng đổi giảm sau khi báo giá -> duyệt phải 409, không có tác dụng phụ, yêu cầu vẫn Pending.
        setListPrice(800_000);
        mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "x")).header("Authorization", admin))
                .andExpect(status().isConflict());
        assertEquals("Pending", body(mvc.perform(get(RETURNS + "/" + rt).header("Authorization", admin))
                .andExpect(status().isOk())).get("status").asText());
        assertEquals(stockBefore, availableStock());
        assertEquals(0, order(o[0]).get("refundedAmount").asInt());
        assertEquals(0, order(o[0]).get("items").get(0).get("returnedQuantity").asInt());

        // Báo giá lại theo returnId (không gửi lại items): bỏ giữ chỗ của chính nó, lấy items từ yêu cầu hiện có.
        JsonNode quote = body(mvc.perform(jsonRequest(post(RETURNS + "/quote"), Map.of("returnId", rt))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(800_000L, quote.get("product").get("exchangeMerchandiseTotalMinor").asLong());
        assertEquals(returnTotal, quote.get("product").get("returnedMerchandiseSettlementMinor").asLong());

        // Báo giá cũ (fingerprint không khớp) khi lưu -> 409; báo giá mới -> lưu được.
        mvc.perform(jsonRequest(put(RETURNS + "/" + rt), Map.of("reason", "DefectiveProduct",
                "pricingQuote", Map.of("fingerprint", "cu"))).header("Authorization", admin)).andExpect(status().isConflict());
        JsonNode updated = body(mvc.perform(jsonRequest(put(RETURNS + "/" + rt), Map.of("reason", "DefectiveProduct",
                "pricingQuote", quote.get("quoteReference"))).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(800_000L, updated.get("exchangeTotal").asLong());

        // Duyệt: số hoàn = trả - đổi (đã chốt), đơn thay thế dùng cùng giá và được bù đủ.
        JsonNode approved = body(mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "ok"))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("Completed", approved.get("status").asText());
        assertEquals(returnTotal - 800_000L, approved.get("totalRefundAmount").asLong());
        JsonNode replacement = order(approved.get("exchangeFulfillmentOrderId").asText());
        assertEquals(0L, replacement.get("grandTotal").asLong());
        assertEquals(returnTotal - 800_000L, order(o[0]).get("refundedAmount").asLong());
    }

    @Test
    void requoteByReturnIdExcludesOwnReservationAndChecksStatus() throws Exception {
        setUp();
        String[] o = deliveredOrder(2);
        // giữ chỗ toàn bộ 2 cái bằng một yêu cầu Pending
        JsonNode created = body(mvc.perform(jsonRequest(post(RETURNS), returnBody(o[0], o[1], 2, "Refund"))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        String rt = created.get("returnCode").asText();
        // báo giá thường với 2 cái: vượt (đã giữ chỗ) -> 400; báo giá theo returnId thì không tính phần của chính nó
        mvc.perform(jsonRequest(post(RETURNS + "/quote"), returnBody(o[0], o[1], 2, "Refund")).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        Map<String, Object> re = returnBody(o[0], o[1], 2, "Refund");
        re.put("returnId", rt);
        JsonNode q = body(mvc.perform(jsonRequest(post(RETURNS + "/quote"), re).header("Authorization", admin))
                .andExpect(status().isOk()));
        assertEquals(created.get("returnTotal").asLong(), q.get("product").get("returnedMerchandiseSettlementMinor").asLong());
        // thuộc đơn khác -> 400
        String[] other = deliveredOrder(1);
        Map<String, Object> wrong = new HashMap<>();
        wrong.put("returnId", rt);
        wrong.put("orderId", other[0]);
        mvc.perform(jsonRequest(post(RETURNS + "/quote"), wrong).header("Authorization", admin)).andExpect(status().isBadRequest());
        // đã Completed -> không báo giá lại được
        mvc.perform(jsonRequest(put(RETURNS + "/" + rt + "/approve"), Map.of("note", "ok")).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(post(RETURNS + "/quote"), Map.of("returnId", rt)).header("Authorization", admin))
                .andExpect(status().isConflict());
        assertNotEquals("", rt);
    }
}

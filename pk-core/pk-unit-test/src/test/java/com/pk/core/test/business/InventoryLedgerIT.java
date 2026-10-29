package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sổ kho (V37): điều chỉnh tồn ghi lịch sử cùng transaction, Idempotency-Key chống cộng hai lần, lỗi nhập liệu có mã ổn định. */
class InventoryLedgerIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ADJUST = V1 + UrlAdminConstant.Inventory.ADJUSTMENTS;
    private static final String MOVEMENTS = V1 + UrlAdminConstant.Inventory.MOVEMENTS;

    private String t;
    private String skuCode;
    private long skuId;
    private String admin;

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
    }

    private Map<String, Object> adjustment(String reason, int delta) {
        return Map.of("skuId", skuId, "quantityDelta", delta, "reason", reason);
    }

    private int onHand() throws Exception {
        for (JsonNode s : body(mvc.perform(get(V1 + UrlAdminConstant.Inventory.STOCK_LEVELS).param("search", skuCode)
                .header("Authorization", admin)).andExpect(status().isOk()))) {
            if (skuCode.equals(s.get("skuCode").asText())) {
                return s.get("onHand").asInt();
            }
        }
        throw new AssertionError("không thấy " + skuCode);
    }

    private JsonNode history() throws Exception {
        return body(mvc.perform(get(MOVEMENTS).param("skuId", String.valueOf(skuId)).header("Authorization", admin))
                .andExpect(status().isOk()));
    }

    @Test
    void adjustmentWritesLedgerRowThatCanBeListedAndRead() throws Exception {
        setUp();
        mvc.perform(jsonRequest(post(ADJUST), Map.of("skuId", skuId, "quantityDelta", 10, "reason", "RECEIPT",
                        "reference", "PN-" + t, "note", "Nhập lô mới")).header("Authorization", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.onHand").value(15));

        JsonNode page = history();
        assertEquals(1, page.get("totalElements").asInt());
        JsonNode row = page.get("content").get(0);
        assertEquals("RECEIPT", row.get("reason").asText());
        assertEquals(10, row.get("quantityDelta").asInt());
        assertEquals(5, row.get("onHandBefore").asInt());
        assertEquals(15, row.get("onHandAfter").asInt());
        assertEquals("PN-" + t, row.get("reference").asText());
        assertEquals(skuCode, row.get("skuCode").asText());
        assertEquals("MAIN", row.get("warehouseId").asText());

        mvc.perform(get(MOVEMENTS + "/" + row.get("id").asLong()).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.onHandAfter").value(15));
        mvc.perform(get(MOVEMENTS + "/999999999").header("Authorization", admin)).andExpect(status().isNotFound());
        // Lọc theo lý do khác -> rỗng; lý do sai -> 400 có field.
        mvc.perform(get(MOVEMENTS).param("skuId", String.valueOf(skuId)).param("reason", "DAMAGE")
                .header("Authorization", admin)).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(MOVEMENTS).param("reason", "RANDOM").header("Authorization", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void sameKeyAndBodyDoesNotAddTwiceAndDifferentBodyConflicts() throws Exception {
        setUp();
        String key = "idem-" + t;
        for (int i = 0; i < 2; i++) {
            mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", 4)).header("Authorization", admin)
                            .header("Idempotency-Key", key))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.onHand").value(9));
        }
        assertEquals(9, onHand());
        assertEquals(1, history().get("totalElements").asInt());

        mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", 7)).header("Authorization", admin)
                        .header("Idempotency-Key", key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(9, onHand());
        mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", 1)).header("Authorization", admin)
                        .header("Idempotency-Key", "ngan"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("IDEMPOTENCY_KEY_INVALID"));
    }

    @Test
    void invalidAdjustmentsHaveStableCodesAndWriteNothing() throws Exception {
        setUp();
        mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", 0)).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("STOCK_ADJUSTMENT_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("quantityDelta"));
        mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", -1)).header("Authorization", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("STOCK_ADJUSTMENT_INVALID"));
        mvc.perform(jsonRequest(post(ADJUST), adjustment("DAMAGE", 1)).header("Authorization", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("STOCK_ADJUSTMENT_INVALID"));
        mvc.perform(jsonRequest(post(ADJUST), adjustment("DAMAGE", -6)).header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("NEGATIVE_STOCK"));
        mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", Integer.MAX_VALUE)).header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("STOCK_OVERFLOW"));
        assertEquals(5, onHand());
        assertEquals(0, history().get("totalElements").asInt());
    }

    @Test
    void parallelAdjustmentsWithDistinctKeysAreAllAppliedOnce() throws Exception {
        setUp();
        int n = 8;
        List<Integer> statuses = runParallel(n, i -> "par-" + t + "-" + i);
        // Request thua cuộc khi tranh hàng có thể nhận 409 STOCK_CONFLICT (gửi lại được); điều bất biến là
        // số +1 đã cộng đúng bằng số request thành công và bằng số dòng sổ - không mất, không cộng đôi.
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 409), statuses.toString());
        long ok = statuses.stream().filter(s -> s == 201).count();
        assertTrue(ok >= 1, statuses.toString());
        assertEquals(5 + ok, onHand());
        assertEquals(ok, history().get("totalElements").asLong());
    }

    @Test
    void parallelAdjustmentsWithSameKeyApplyExactlyOnce() throws Exception {
        setUp();
        List<Integer> statuses = runParallel(6, i -> "same-" + t);
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 409), statuses.toString());
        assertTrue(statuses.contains(201), statuses.toString());
        assertEquals(6, onHand(), "chỉ được cộng +1 đúng một lần");
        assertEquals(1, history().get("totalElements").asInt());
    }

    /** Gửi n yêu cầu RECEIPT +1 đồng thời, trả về các mã HTTP. */
    private List<Integer> runParallel(int n, java.util.function.IntFunction<String> keyOf) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String key = keyOf.apply(i);
                Callable<Integer> call = () -> mvc.perform(jsonRequest(post(ADJUST), adjustment("RECEIPT", 1))
                        .header("Authorization", admin).header("Idempotency-Key", key)).andReturn().getResponse().getStatus();
                futures.add(pool.submit(call));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}

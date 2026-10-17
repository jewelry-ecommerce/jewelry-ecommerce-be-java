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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sản phẩm ghim ở ô tìm kiếm: CRUD, 4 ô, đổi chỗ khi sửa, validate, danh sách công khai chỉ gồm ghim đang hiệu lực. */
class SearchPinnedIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String PINS = V1 + UrlAdminConstant.Catalog.SEARCH_PINNED;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String PUBLIC = V1 + UrlConstant.Product.SEARCH_PINNED;

    private long product(String admin, long categoryId, String t, String s, boolean publish) throws Exception {
        long id = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P" + s + "-" + t, "name", "Ghim" + s + " " + t, "slug", "ghim" + s + "-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU" + s + "-" + t, "listPrice", 100_000, "onHand", 5,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        if (publish) {
            mvc.perform(patch(CATALOG + "/" + id + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        }
        return id;
    }

    private String code(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return body(r).get("errors").get(0).get("code").asText();
    }

    @Test
    void landingPinsLifecycleSwapAndPublicView() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6);
        String admin = bearer(adminAccessToken());
        // dọn các ghim landing có sẵn của DB dùng chung
        for (JsonNode p : body(mvc.perform(get(PINS).header("Authorization", admin))).get("list")) {
            mvc.perform(delete(PINS + "/" + p.get("id").asLong()).header("Authorization", admin)).andExpect(status().isOk());
        }
        long cat = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long pa = product(admin, cat, t, "A", true);
        long pb = product(admin, cat, t, "B", true);
        long pc = product(admin, cat, t, "C", false); // chưa xuất bản
        Instant now = Instant.now();

        // validate
        mvc.perform(jsonRequest(post(PINS), Map.of("productId", 99999999L, "position", 0)).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(PINS), Map.of("position", 0)).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(PINS), Map.of("productId", pa, "position", 4)).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(PINS), Map.of("productId", pa, "position", 0, "startDate", now.toString(),
                "endDate", now.minus(1, ChronoUnit.HOURS).toString())).header("Authorization", admin)).andExpect(status().isBadRequest());

        Map<String, Object> reqA = new HashMap<>(Map.of("productId", pa, "position", 0, "startDate", now.minus(1, ChronoUnit.DAYS).toString()));
        JsonNode a = body(mvc.perform(jsonRequest(post(PINS), reqA).header("Authorization", admin)).andExpect(status().isCreated()));
        assertEquals(0, a.get("position").asInt());
        assertTrue(a.get("isActive").asBoolean());
        assertNull(a.get("keywordPattern") == null || a.get("keywordPattern").isNull() ? null : "x");
        long idA = a.get("id").asLong();

        // trùng ô -> 409; trùng sản phẩm -> 409; không gửi position -> ô trống đầu tiên
        assertEquals("SEARCH_PINNED_POSITION_TAKEN", code(mvc.perform(jsonRequest(post(PINS), Map.of("productId", pb, "position", 0))
                .header("Authorization", admin)).andExpect(status().isConflict())));
        assertEquals("SEARCH_PINNED_PRODUCT_DUPLICATE", code(mvc.perform(jsonRequest(post(PINS), Map.of("productId", pa, "position", 1))
                .header("Authorization", admin)).andExpect(status().isConflict())));
        JsonNode b = body(mvc.perform(jsonRequest(post(PINS), Map.of("productId", pb, "startDate", now.minus(1, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        assertEquals(1, b.get("position").asInt());
        long idB = b.get("id").asLong();
        long idC = body(mvc.perform(jsonRequest(post(PINS), Map.of("productId", pc, "position", 2)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();

        // đổi chỗ: A sang ô 1 -> B về ô 0
        JsonNode movedA = body(mvc.perform(jsonRequest(patch(PINS + "/" + idA), Map.of("position", 1)).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, movedA.get("position").asInt());
        JsonNode list = body(mvc.perform(get(PINS).header("Authorization", admin))).get("list");
        assertEquals(3, list.size());
        assertEquals(3, body(mvc.perform(get(PINS).header("Authorization", admin))).get("total").asInt());
        assertEquals(idB, list.get(0).get("id").asLong());
        assertEquals(0, list.get(0).get("position").asInt());

        // công khai: theo thứ tự ô, bỏ sản phẩm chưa xuất bản
        JsonNode pub = body(mvc.perform(get(PUBLIC)).andExpect(status().isOk()));
        assertEquals(2, pub.size());
        assertEquals(pb, pub.get(0).get("id").asLong());
        assertEquals(pa, pub.get(1).get("id").asLong());

        // tắt B / ghim hết hạn -> không hiện
        mvc.perform(jsonRequest(patch(PINS + "/" + idB), Map.of("isActive", false)).header("Authorization", admin)).andExpect(status().isOk());
        assertEquals(1, body(mvc.perform(get(PUBLIC))).size());
        mvc.perform(jsonRequest(patch(PINS + "/" + idA), Map.of("endDate", now.minus(1, ChronoUnit.HOURS).toString())).header("Authorization", admin))
                .andExpect(status().isOk());
        assertEquals(0, body(mvc.perform(get(PUBLIC))).size());
        JsonNode cleared = body(mvc.perform(jsonRequest(patch(PINS + "/" + idA), Map.of("clearEndDate", true)).header("Authorization", admin)).andExpect(status().isOk()));
        assertTrue(cleared.get("endDate") == null || cleared.get("endDate").isNull());
        assertEquals(1, body(mvc.perform(get(PUBLIC))).size());

        // ghim theo từ khoá nằm phạm vi riêng (ô 0 không đụng landing), không hiện ở công khai landing
        JsonNode kw = body(mvc.perform(jsonRequest(post(PINS), Map.of("productId", pa, "position", 0, "keywordPattern", "  Nhẫn Vàng "))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        assertEquals("nhẫn vàng", kw.get("keywordPattern").asText());
        assertEquals(1, body(mvc.perform(get(PUBLIC))).size());

        // xoá
        assertEquals("Đã xoá sản phẩm ghim", body(mvc.perform(delete(PINS + "/" + idC).header("Authorization", admin)).andExpect(status().isOk())).get("message").asText());
        mvc.perform(delete(PINS + "/" + idC).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(patch(PINS + "/999999999"), Map.of("position", 1)).header("Authorization", admin)).andExpect(status().isNotFound());
    }
}

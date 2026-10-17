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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cấu hình banner chèn vào danh sách sản phẩm: CRUD admin, validate (MIX_MATCH cần SKU thật), trùng đích, API công khai theo lịch. */
class ProductListBannerIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CFG = V1 + UrlAdminConstant.Banner.PRODUCT_LIST_CONFIGS;
    private static final String PUBLIC = V1 + UrlConstant.Banner.PRODUCT_LIST;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;

    @Test
    void crudValidationAndPublicSchedule() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6);
        String admin = bearer(adminAccessToken());
        Instant now = Instant.now();
        long cat = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of("categoryId", cat, "code", "PB-" + t, "name", "Sp " + t,
                        "slug", "sp-" + t, "variants", List.of(Map.of("skuCode", "SB-" + t, "listPrice", 100_000, "onHand", 3,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin))).get(0).get("id").asLong();

        Map<String, Object> link = Map.of("imageUrl", "/img/a.jpg", "actionType", "LINK_URL", "actionUrl", "/khuyen-mai",
                "startTime", now.minus(1, ChronoUnit.DAYS).toString());
        Map<String, Object> mix = Map.of("imageUrl", "/img/b.jpg", "actionType", "MIX_MATCH",
                "mixMatchSkus", List.of(Map.of("variationId", String.valueOf(skuId))),
                "startTime", now.minus(1, ChronoUnit.DAYS).toString());

        // validate
        mvc.perform(jsonRequest(post(CFG), Map.of("targetSlug", "dm-" + t, "banners", List.of(link))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CFG), Map.of("conditionType", "CATEGORY", "targetSlug", "dm-" + t)).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CFG), Map.of("conditionType", "CATEGORY", "targetSlug", "dm-" + t,
                "banners", List.of(Map.of("actionType", "LINK_URL")))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CFG), Map.of("conditionType", "CATEGORY", "targetSlug", "dm-" + t,
                "banners", List.of(Map.of("imageUrl", "/i.jpg", "actionType", "MIX_MATCH")))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CFG), Map.of("conditionType", "CATEGORY", "targetSlug", "dm-" + t,
                "banners", List.of(Map.of("imageUrl", "/i.jpg", "actionType", "MIX_MATCH", "mixMatchSkus", List.of(Map.of("variationId", "99999999")))))).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CFG), Map.of("conditionType", "CMS_STOREFRONT", "targetSlug", "trang-" + t, "banners", List.of(link))).header("Authorization", admin))
                .andExpect(status().isBadRequest()); // thiếu targetName

        // tạo
        Map<String, Object> req = new HashMap<>(Map.of("conditionType", "CATEGORY", "targetId", String.valueOf(cat), "targetSlug", "dm-" + t,
                "targetName", "DM " + t, "categoryPathSlugs", List.of("dm-" + t), "banners", List.of(link, mix)));
        JsonNode created = body(mvc.perform(jsonRequest(post(CFG), req).header("Authorization", admin)).andExpect(status().isCreated()));
        long id = created.get("id").asLong();
        assertEquals(2, created.get("banners").size());
        assertTrue(created.get("banners").get(0).get("id").asText().length() > 10, "server cấp id cho banner");
        assertEquals(skuId, created.get("banners").get(1).get("mixMatchSkus").get(0).get("variationId").asLong());
        assertTrue(created.get("isActive").asBoolean());

        // trùng đích -> 409
        mvc.perform(jsonRequest(post(CFG), req).header("Authorization", admin)).andExpect(status().isConflict());

        // danh sách + lọc
        assertTrue(body(mvc.perform(get(CFG).param("conditionType", "CATEGORY").header("Authorization", admin))).size() >= 1);
        assertEquals(0, body(mvc.perform(get(CFG).param("conditionType", "CMS_STOREFRONT").param("x", t).header("Authorization", admin))).findValuesAsText("targetSlug").stream().filter(s -> s.equals("dm-" + t)).count());

        // công khai: cả 2 banner; banner tương lai/tắt không hiện
        assertEquals(2, body(mvc.perform(get(PUBLIC).param("conditionType", "CATEGORY").param("targetSlug", "dm-" + t)).andExpect(status().isOk())).get("banners").size());
        Map<String, Object> future = new HashMap<>(link);
        future.put("startTime", now.plus(3, ChronoUnit.DAYS).toString());
        Map<String, Object> off = new HashMap<>(link);
        off.put("enabled", false);
        JsonNode updated = body(mvc.perform(jsonRequest(patch(CFG + "/" + id), Map.of("banners", List.of(link, future, off))).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(3, updated.get("banners").size());
        assertEquals(1, body(mvc.perform(get(PUBLIC).param("conditionType", "CATEGORY").param("targetSlug", "dm-" + t))).get("banners").size());

        // tắt cấu hình -> công khai không có (data null / không có banners)
        mvc.perform(jsonRequest(patch(CFG + "/" + id), Map.of("isActive", false)).header("Authorization", admin)).andExpect(status().isOk());
        JsonNode none = body(mvc.perform(get(PUBLIC).param("conditionType", "CATEGORY").param("targetSlug", "dm-" + t)).andExpect(status().isOk()));
        assertTrue(none.isNull() || none.isMissingNode() || !none.has("banners"));

        // xoá
        mvc.perform(delete(CFG + "/" + id).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(delete(CFG + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(patch(CFG + "/999999999"), Map.of("isActive", true)).header("Authorization", admin)).andExpect(status().isNotFound());
    }
}

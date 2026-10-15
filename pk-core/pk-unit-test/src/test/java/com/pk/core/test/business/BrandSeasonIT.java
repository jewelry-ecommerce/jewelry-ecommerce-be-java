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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V27: thương hiệu + mùa (CRUD, validate, xoá mềm, chặn xoá khi đang dùng) và gắn vào sản phẩm (nhận/trả/lọc). */
class BrandSeasonIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String BRANDS = V1 + UrlAdminConstant.Catalog.BRANDS;
    private static final String SEASONS = V1 + UrlAdminConstant.Catalog.SEASONS;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;

    private String errorCode(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return body(r).get("errors").get(0).get("code").asText();
    }

    @Test
    void brandCrudValidationAndSoftDelete() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String admin = bearer(adminAccessToken());
        long id = body(mvc.perform(jsonRequest(post(BRANDS), Map.of("name", "Thương hiệu " + t, "code", "br-" + t,
                        "websiteUrl", "https://example.com")).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode d = body(mvc.perform(get(BRANDS + "/" + id).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("BR-" + t, d.get("code").asText(), "code chuẩn hoá IN HOA");
        assertEquals("ACTIVE", d.get("status").asText());

        // trùng mã (không phân biệt hoa/thường) -> 409
        assertEquals("BRAND_CODE_DUPLICATE", errorCode(mvc.perform(jsonRequest(post(BRANDS),
                Map.of("name", "Khác", "code", "br-" + t.toLowerCase())).header("Authorization", admin))
                .andExpect(status().isConflict())));
        // thiếu tên / mã sai / url sai / status sai -> 400
        mvc.perform(jsonRequest(post(BRANDS), Map.of("code", "OK-" + t)).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(BRANDS), Map.of("name", "X", "code", "a b")).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(BRANDS), Map.of("name", "X", "code", "OK2-" + t, "websiteUrl", "ftp://x")).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch(BRANDS + "/" + id), Map.of("status", "Weird")).header("Authorization", admin)).andExpect(status().isBadRequest());

        // PATCH một phần + status không phân biệt hoa/thường
        JsonNode upd = body(mvc.perform(jsonRequest(patch(BRANDS + "/" + id), Map.of("name", "Tên mới", "status", "inactive"))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("Tên mới", upd.get("name").asText());
        assertEquals("INACTIVE", upd.get("status").asText());
        assertEquals("BR-" + t, upd.get("code").asText());

        // danh sách + lọc
        assertEquals(1, body(mvc.perform(get(BRANDS).header("Authorization", admin).param("search", "br-" + t.toLowerCase())
                .param("status", "INACTIVE"))).get("totalElements").asLong());
        assertEquals(0, body(mvc.perform(get(BRANDS).header("Authorization", admin).param("search", "br-" + t.toLowerCase())
                .param("status", "ACTIVE"))).get("totalElements").asLong());
        mvc.perform(get(BRANDS).header("Authorization", admin).param("status", "???")).andExpect(status().isBadRequest());

        // quyền
        mvc.perform(get(BRANDS)).andExpect(status().isUnauthorized());
        mvc.perform(get(BRANDS).header("Authorization", bearer(customerAccessToken()))).andExpect(status().isForbidden());

        // xoá mềm: biến mất, mã được tái sử dụng
        mvc.perform(delete(BRANDS + "/" + id).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get(BRANDS + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(post(BRANDS), Map.of("name", "Dùng lại mã", "code", "BR-" + t)).header("Authorization", admin))
                .andExpect(status().isCreated());
    }

    @Test
    void seasonCrudAndValidation() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String admin = bearer(adminAccessToken());
        long id = body(mvc.perform(jsonRequest(post(SEASONS), Map.of("name", "Mùa đông " + t, "code", "dong-" + t,
                        "startMonth", 11, "endMonth", 2, "sortOrder", 5)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode d = body(mvc.perform(get(SEASONS + "/" + id).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(11, d.get("startMonth").asInt());
        assertEquals(2, d.get("endMonth").asInt(), "được vắt năm");
        assertTrue(d.get("isActive").asBoolean());
        assertEquals("SEASON_CODE_DUPLICATE", errorCode(mvc.perform(jsonRequest(post(SEASONS),
                Map.of("name", "Trùng", "code", "DONG-" + t, "startMonth", 1, "endMonth", 2)).header("Authorization", admin))
                .andExpect(status().isConflict())));
        mvc.perform(jsonRequest(post(SEASONS), Map.of("name", "X", "code", "M-" + t, "startMonth", 0, "endMonth", 2))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(SEASONS), Map.of("name", "X", "code", "M2-" + t, "startMonth", 1)).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch(SEASONS + "/" + id), Map.of("endMonth", 13)).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        assertTrue(!body(mvc.perform(jsonRequest(patch(SEASONS + "/" + id), Map.of("isActive", false)).header("Authorization", admin)
                .accept("application/json"))).get("isActive").asBoolean());
        assertEquals(0, body(mvc.perform(get(SEASONS).header("Authorization", admin).param("search", "dong-" + t.toLowerCase())
                .param("isActive", "true"))).get("totalElements").asLong());
        assertEquals(1, body(mvc.perform(get(SEASONS).header("Authorization", admin).param("search", "dong-" + t.toLowerCase())
                .param("isActive", "false"))).get("totalElements").asLong());
        mvc.perform(delete(SEASONS + "/" + id).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get(SEASONS + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void productBrandSeasonAssignFilterAndInUse() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long brandId = body(mvc.perform(jsonRequest(post(BRANDS), Map.of("name", "Hiệu " + t, "code", "BP-" + t))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
        long seasonId = body(mvc.perform(jsonRequest(post(SEASONS), Map.of("name", "Mùa " + t, "code", "SP-" + t,
                "startMonth", 3, "endMonth", 5)).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();

        Map<String, Object> create = new HashMap<>(Map.of("categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t,
                "slug", "nhan-" + t, "brandId", brandId, "seasonId", seasonId,
                "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 500_000, "onHand", 5, "isDefault", true, "status", "PUBLISHED"))));
        JsonNode created = body(mvc.perform(jsonRequest(post(CATALOG), create).header("Authorization", admin)).andExpect(status().isCreated()));
        long productId = created.get("id").asLong();
        assertEquals(brandId, created.get("brandId").asLong());
        assertEquals("Hiệu " + t, created.get("brandName").asText());
        assertEquals(seasonId, created.get("seasonId").asLong());
        assertEquals("Mùa " + t, created.get("seasonName").asText());

        // sản phẩm thứ hai không gắn gì
        Map<String, Object> plain = new HashMap<>(Map.of("categoryId", categoryId, "code", "Q-" + t, "name", "Dây " + t,
                "slug", "day-" + t, "variants", List.of(Map.of("skuCode", "SKQ-" + t, "listPrice", 100_000, "onHand", 5, "isDefault", true, "status", "PUBLISHED"))));
        long plainId = body(mvc.perform(jsonRequest(post(CATALOG), plain).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();

        // lọc
        assertEquals(List.of(productId), ids(body(mvc.perform(get(CATALOG).header("Authorization", admin)
                .param("categorySlug", "dm-" + t).param("brandId", String.valueOf(brandId))))));
        assertEquals(List.of(productId), ids(body(mvc.perform(get(CATALOG).header("Authorization", admin)
                .param("categorySlug", "dm-" + t).param("seasonId", String.valueOf(seasonId))))));
        assertEquals(List.of(productId), ids(body(mvc.perform(get(CATALOG).header("Authorization", admin)
                .param("categorySlug", "dm-" + t).param("seasonUuid", String.valueOf(seasonId))))), "alias seasonUuid của FE");
        assertEquals(0, body(mvc.perform(get(CATALOG).header("Authorization", admin).param("brandId", "999999999"))).get("totalElements").asLong());
        mvc.perform(get(CATALOG).header("Authorization", admin).param("seasonUuid", "abc-xyz")).andExpect(status().isBadRequest());
        assertEquals(1, body(mvc.perform(get(CATALOG + "/counts").header("Authorization", admin)
                .param("categorySlug", "dm-" + t).param("brandId", String.valueOf(brandId)))).get("all").asLong());

        // đang dùng -> không xoá được
        assertEquals("BRAND_IN_USE", errorCode(mvc.perform(delete(BRANDS + "/" + brandId).header("Authorization", admin)).andExpect(status().isConflict())));
        assertEquals("SEASON_IN_USE", errorCode(mvc.perform(delete(SEASONS + "/" + seasonId).header("Authorization", admin)).andExpect(status().isConflict())));

        // gán id không tồn tại -> 400
        Map<String, Object> bad = new HashMap<>(Map.of("categoryId", categoryId, "name", "Nhẫn " + t, "brandId", 999_999_999L));
        mvc.perform(jsonRequest(put(CATALOG + "/" + plainId), bad).header("Authorization", admin)).andExpect(status().isBadRequest());
        // ngừng kích hoạt thương hiệu: gán mới bị từ chối, sản phẩm đã gắn vẫn cập nhật được
        mvc.perform(jsonRequest(patch(BRANDS + "/" + brandId), Map.of("status", "INACTIVE")).header("Authorization", admin)).andExpect(status().isOk());
        Map<String, Object> toInactive = new HashMap<>(Map.of("categoryId", categoryId, "name", "Dây " + t, "brandId", brandId));
        mvc.perform(jsonRequest(put(CATALOG + "/" + plainId), toInactive).header("Authorization", admin)).andExpect(status().isBadRequest());
        Map<String, Object> keep = new HashMap<>(Map.of("categoryId", categoryId, "name", "Nhẫn mới " + t, "brandId", brandId, "seasonId", seasonId));
        assertEquals(brandId, body(mvc.perform(jsonRequest(put(CATALOG + "/" + productId), keep).header("Authorization", admin))
                .andExpect(status().isOk())).get("brandId").asLong());

        // PUT không gửi brand/season = bỏ gán -> xoá được thương hiệu/mùa
        Map<String, Object> clear = new HashMap<>(Map.of("categoryId", categoryId, "name", "Nhẫn mới " + t));
        JsonNode cleared = body(mvc.perform(jsonRequest(put(CATALOG + "/" + productId), clear).header("Authorization", admin)).andExpect(status().isOk()));
        assertTrue(cleared.get("brandId") == null || cleared.get("brandId").isNull());
        mvc.perform(delete(BRANDS + "/" + brandId).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(delete(SEASONS + "/" + seasonId).header("Authorization", admin)).andExpect(status().isOk());
    }

    private static List<Long> ids(JsonNode page) {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        page.get("content").forEach(p -> out.add(p.get("id").asLong()));
        return out;
    }
}

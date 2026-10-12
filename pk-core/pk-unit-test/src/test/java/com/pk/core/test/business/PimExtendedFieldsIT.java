package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dữ liệu PIM mở rộng (V18) lưu/đọc qua API: SEO, thuế, kích thước, giá vốn/B2B, productInfos, phối kèm. */
class PimExtendedFieldsIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;

    private String auth;
    private long categoryId;
    private String t;

    private void setUp() throws Exception {
        t = UUID.randomUUID().toString().substring(0, 8);
        auth = bearer(adminAccessToken());
        categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", auth))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> sku(String code, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skuCode", code);
        m.put("listPrice", 2_000_000);
        m.put("onHand", 5);
        m.put("isDefault", true);
        m.putAll(extra);
        return m;
    }

    private Map<String, Object> product(String code, List<Map<String, Object>> variants, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryId", categoryId);
        m.put("code", code);
        m.put("name", "SP " + code);
        m.put("slug", code.toLowerCase());
        m.put("variants", variants);
        m.putAll(extra);
        return m;
    }

    private JsonNode createProduct(String code, List<Map<String, Object>> variants, Map<String, Object> extra) throws Exception {
        return body(mvc.perform(jsonRequest(post(CATALOG), product(code, variants, extra)).header("Authorization", auth))
                .andExpect(status().isCreated()));
    }

    @Test
    void extendedFieldsRoundTripAndAreNotLeakedToStorefront() throws Exception {
        setUp();
        JsonNode other = createProduct("PO-" + t, List.of(sku("SKO-" + t, Map.of())), Map.of());
        long otherId = other.get("id").asLong();

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("unitOfMeasure", "box");
        extra.put("taxRateOverride", 8);
        extra.put("weightGram", 12.5);
        extra.put("length", 30);
        extra.put("width", 20);
        extra.put("height", 10);
        extra.put("metaTitle", "Tiêu đề SEO");
        extra.put("metaDescription", "Mô tả SEO");
        extra.put("metaKeywords", "nhẫn, vàng");
        extra.put("productInfos", List.of(Map.of("title", "Bảo hành", "description", "12 tháng", "sortOrder", 1),
                Map.of("description", "Giao nhanh", "sortOrder", 0)));
        extra.put("productMixMatch", List.of(otherId));
        JsonNode created = createProduct("PA-" + t, List.of(sku("SKA-" + t, Map.of("costPrice", 1_200_000,
                "b2bWholesalePrice", 1_800_000, "taxRateOverride", 5, "length", 2, "width", 3, "height", 4))), extra);
        long id = created.get("id").asLong();

        for (JsonNode d : List.of(created, body(mvc.perform(get(CATALOG + "/" + id).header("Authorization", auth))
                .andExpect(status().isOk())))) {
            assertEquals("BOX", d.get("unitOfMeasure").asText());
            assertEquals(8, d.get("taxRateOverride").asInt());
            assertEquals(30, d.get("length").asInt());
            assertEquals("Tiêu đề SEO", d.get("metaTitle").asText());
            assertEquals(2, d.get("productInfos").size());
            assertEquals("Giao nhanh", d.get("productInfos").get(0).get("description").asText()); // theo sortOrder
            assertEquals("PO-" + t, d.get("productMixMatch").get(0).get("code").asText());
            assertEquals("PO-" + t, d.get("productMixMatch").get(0).get("spuCode").asText());
            JsonNode s = d.get("skus").get(0);
            assertEquals(1_200_000, s.get("costPrice").asInt());
            assertEquals(1_800_000, s.get("b2bWholesalePrice").asInt());
            assertEquals(5, s.get("taxRateOverride").asInt());
            assertEquals(4, s.get("height").asInt());
        }

        // Publish thẳng từ DRAFT; storefront thấy SEO + thông tin bổ sung nhưng KHÔNG thấy thuế/giá vốn/giá sỉ.
        mvc.perform(patch(CATALOG + "/" + id + "/publish").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "/pa-" + t.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metaTitle").value("Tiêu đề SEO"))
                .andExpect(jsonPath("$.data.productInfos.length()").value(2))
                .andExpect(jsonPath("$.data.taxRateOverride").doesNotExist())
                .andExpect(jsonPath("$.data.skus[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.skus[0].b2bWholesalePrice").doesNotExist())
                .andExpect(jsonPath("$.data.productMixMatch").doesNotExist());
    }

    @Test
    void invalidInputIsRejectedAndUpdateSemanticsAreExplicit() throws Exception {
        setUp();
        JsonNode other = createProduct("QO-" + t, List.of(sku("SQO-" + t, Map.of())), Map.of());
        long otherId = other.get("id").asLong();
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("metaTitle", "Tiêu đề");
        extra.put("productInfos", List.of(Map.of("description", "Một", "sortOrder", 0)));
        extra.put("productMixMatch", List.of(otherId));
        JsonNode created = createProduct("QA-" + t, List.of(sku("SQA-" + t, Map.of())), extra);
        long id = created.get("id").asLong();
        long skuId = created.get("skus").get(0).get("id").asLong();

        // Validation khi tạo.
        mvc.perform(jsonRequest(post(CATALOG), product("QB-" + t, List.of(sku("SQB-" + t, Map.of())),
                Map.of("unitOfMeasure", "KG"))).header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CATALOG), product("QC-" + t, List.of(sku("SQC-" + t, Map.of("taxRateOverride", 101))),
                Map.of())).header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CATALOG), product("QD-" + t, List.of(sku("SQD-" + t, Map.of("costPrice", -1))),
                Map.of())).header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(CATALOG), product("QE-" + t, List.of(sku("SQE-" + t, Map.of())),
                Map.of("productMixMatch", List.of(999999999L)))).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(post(CATALOG), product("QF-" + t, List.of(sku("SQF-" + t, Map.of())),
                Map.of("productInfos", List.of(Map.of("description", " ")))))
                .header("Authorization", auth)).andExpect(status().isBadRequest());

        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("categoryId", categoryId);
        upd.put("name", "Tên mới");
        // Đổi mã -> 400 CODE_IMMUTABLE; tự phối kèm chính mình -> 400.
        upd.put("code", "KHAC-" + t);
        mvc.perform(jsonRequest(put(CATALOG + "/" + id), upd).header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("CODE_IMMUTABLE"));
        upd.put("code", "QA-" + t);
        upd.put("productMixMatch", List.of(id));
        mvc.perform(jsonRequest(put(CATALOG + "/" + id), upd).header("Authorization", auth)).andExpect(status().isBadRequest());
        upd.remove("productMixMatch");

        // PUT không gửi productInfos/productMixMatch -> GIỮ NGUYÊN; metaTitle không gửi -> bị xoá (PUT = thay thế).
        JsonNode after = body(mvc.perform(jsonRequest(put(CATALOG + "/" + id), upd).header("Authorization", auth))
                .andExpect(status().isOk()));
        assertEquals("Tên mới", after.get("name").asText());
        assertEquals(1, after.get("productInfos").size());
        assertEquals(1, after.get("productMixMatch").size());
        assertFalse(after.has("metaTitle"));
        assertEquals("EACH", after.get("unitOfMeasure").asText());

        // [] -> xoá hết.
        upd.put("productInfos", List.of());
        upd.put("productMixMatch", List.of());
        JsonNode cleared = body(mvc.perform(jsonRequest(put(CATALOG + "/" + id), upd).header("Authorization", auth))
                .andExpect(status().isOk()));
        assertEquals(0, cleared.get("productInfos").size());
        assertEquals(0, cleared.get("productMixMatch").size());

        // Sửa SKU (kể cả gửi onHand = 0 như FE đang làm) KHÔNG đặt lại tồn kho; giá vốn lưu lại.
        Map<String, Object> skuUpd = sku("SQA-" + t, Map.of("onHand", 0, "costPrice", 900_000, "listPrice", 2_100_000));
        mvc.perform(jsonRequest(put(CATALOG + "/" + id + "/variants/" + skuId), skuUpd).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.costPrice").value(900_000))
                .andExpect(jsonPath("$.data.availableStock").value(5));
        JsonNode detail = body(mvc.perform(get(CATALOG + "/" + id).header("Authorization", auth)));
        assertEquals(5, detail.get("skus").get(0).get("availableStock").asInt());
        assertEquals(2_100_000, detail.get("skus").get(0).get("listPrice").asInt());

        // Thư viện media: ảnh chính đồng bộ thumbnail; PUT sản phẩm với cùng thumbnail không làm mất media.
        JsonNode media = body(mvc.perform(jsonRequest(post(CATALOG + "/" + id + "/media"),
                Map.of("url", "/uploads/a-" + t + ".jpg", "primary", true, "sortOrder", 0)).header("Authorization", auth))
                .andExpect(status().isCreated()));
        assertTrue(media.has("id"));
        JsonNode withMedia = body(mvc.perform(get(CATALOG + "/" + id).header("Authorization", auth)));
        assertEquals(1, withMedia.get("media").size());
        assertEquals("/uploads/a-" + t + ".jpg", withMedia.get("thumbnailUrl").asText());
        upd.put("thumbnailUrl", "/uploads/a-" + t + ".jpg");
        JsonNode again = body(mvc.perform(jsonRequest(put(CATALOG + "/" + id), upd).header("Authorization", auth))
                .andExpect(status().isOk()));
        assertEquals(1, again.get("media").size());
    }
}

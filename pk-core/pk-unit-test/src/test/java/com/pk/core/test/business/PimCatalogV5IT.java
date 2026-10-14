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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE v5 PIM: danh mục (code/status/thuộc tính/cây 3 cấp), giá trị thuộc tính, filters + lọc client, vòng đời sản phẩm. */
class PimCatalogV5IT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String CATEGORIES = V1 + UrlAdminConstant.Catalog.CATEGORIES;
    private static final String ATTRIBUTES = V1 + UrlAdminConstant.Catalog.ATTRIBUTES;
    private static final String STORE = V1 + UrlConstant.Product.BASE;

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String admin() throws Exception {
        return bearer(adminAccessToken());
    }

    private Map<String, Object> categoryBody(String name, String code, Long parentId, String status, List<Long> attrs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("slug", "s-" + code.toLowerCase());
        m.put("code", code);
        m.put("sortOrder", 0);
        if (parentId != null) {
            m.put("parentId", parentId);
        }
        if (status != null) {
            m.put("status", status);
        }
        if (attrs != null) {
            m.put("directAttributeIds", attrs);
        }
        return m;
    }

    private JsonNode createCategory(String auth, Map<String, Object> body) throws Exception {
        return body(mvc.perform(jsonRequest(post(CATEGORIES), body).header("Authorization", auth))
                .andExpect(status().isCreated()));
    }

    private JsonNode createAttribute(String auth, Map<String, Object> body) throws Exception {
        return body(mvc.perform(jsonRequest(post(ATTRIBUTES), body).header("Authorization", auth))
                .andExpect(status().isCreated()));
    }

    private Map<String, Object> value(String code, String value, String image) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("value", value);
        if (image != null) {
            m.put("imageUrl", image);
        }
        return m;
    }

    /** Tạo sản phẩm 1 SKU trong category, trả [productId, skuId]. */
    private long[] createProduct(String auth, long categoryId, String t, int price) throws Exception {
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "sizeLabel", "M", "listPrice", price,
                                "onHand", 5, "isDefault", true)))).header("Authorization", auth))
                .andExpect(status().isCreated())).get("id").asLong();
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", auth))
                .andExpect(status().isOk())).get(0).get("id").asLong();
        return new long[] {productId, skuId};
    }

    private void bind(String auth, long[] ids, long attributeId, String value) throws Exception {
        mvc.perform(jsonRequest(put(CATALOG + "/" + ids[0] + "/variants/" + ids[1] + "/attributes"),
                        Map.of("values", List.of(Map.of("attributeId", attributeId, "value", value))))
                        .header("Authorization", auth))
                .andExpect(status().isOk());
    }

    private void publish(String auth, long productId) throws Exception {
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", auth))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ Danh mục

    @Test
    void categoryHasCodeStatusInheritedAttributesAndMaxThreeLevels() throws Exception {
        String auth = admin();
        String t = tag().toUpperCase();
        long attr = createAttribute(auth, Map.of("name", "Chất liệu " + t, "code", "mat_" + t, "type", "SELECT",
                "values", List.of(value("gold", "Vàng", null)))).get("id").asLong();

        JsonNode root = createCategory(auth, categoryBody("Gốc " + t, "ROOT-" + t, null, null, List.of(attr)));
        assertEquals("ROOT-" + t, root.get("code").asText());
        assertEquals("ACTIVE", root.get("status").asText());
        assertEquals(1, root.get("level").asInt());
        assertEquals(attr, root.get("directAttributeIds").get(0).asLong());
        long rootId = root.get("id").asLong();

        JsonNode child = createCategory(auth, categoryBody("Con " + t, "CHILD-" + t, rootId, null, List.of()));
        assertEquals(2, child.get("level").asInt());
        assertEquals(0, child.get("directAttributeIds").size());
        assertEquals(attr, child.get("inheritedAttributeIds").get(0).asLong());
        assertEquals(attr, child.get("effectiveAttributeIds").get(0).asLong());
        assertEquals("s-root-" + t.toLowerCase(), child.get("ancestorCategorySlugs").get(0).asText());
        long childId = child.get("id").asLong();

        // Gán trùng thuộc tính đã kế thừa -> 400; mã trùng -> 409.
        mvc.perform(jsonRequest(post(CATEGORIES), categoryBody("Con2 " + t, "C2-" + t, rootId, null, List.of(attr)))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CATEGORY_ATTRIBUTE_INVALID"));
        mvc.perform(jsonRequest(post(CATEGORIES), categoryBody("Khác " + t, "ROOT-" + t, null, null, null))
                        .header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CODE"));

        // Cấp 3 được, cấp 4 bị chặn.
        long level3 = createCategory(auth, categoryBody("Cháu " + t, "L3-" + t, childId, null, null)).get("id").asLong();
        mvc.perform(jsonRequest(post(CATEGORIES), categoryBody("Chắt " + t, "L4-" + t, level3, null, null))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CATEGORY_PARENT_INVALID"));
        // Vòng lặp: đưa gốc vào làm con của cháu -> 400.
        mvc.perform(jsonRequest(put(CATEGORIES + "/" + rootId), categoryBody("Gốc " + t, "ROOT-" + t, level3, null, null))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CATEGORY_PARENT_INVALID"));
        // Cha không tồn tại -> 400.
        mvc.perform(jsonRequest(post(CATEGORIES), categoryBody("Mồ côi " + t, "OR-" + t, 999_999_999L, null, null))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest());

        // Tắt danh mục con: storefront không còn con và cháu; admin vẫn thấy.
        mvc.perform(jsonRequest(put(CATEGORIES + "/" + childId),
                        categoryBody("Con " + t, "CHILD-" + t, rootId, "INACTIVE", null)).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.effectiveAttributeIds[0]").value(attr)); // directAttributeIds null = giữ nguyên
        JsonNode publicList = body(mvc.perform(get(V1 + UrlConstant.Product.CATEGORIES)).andExpect(status().isOk()));
        boolean rootVisible = false;
        for (JsonNode c : publicList) {
            long id = c.get("id").asLong();
            assertTrue(id != childId && id != level3, "danh mục INACTIVE và con của nó không được public");
            rootVisible |= id == rootId;
        }
        assertTrue(rootVisible);
    }

    // ------------------------------------------------------------------ Thuộc tính

    @Test
    void attributeValuesKeepIdCodeImageAndProtectSkuBindings() throws Exception {
        String auth = admin();
        String t = tag();
        JsonNode created = createAttribute(auth, Map.of("name", "Màu " + t, "code", "color_" + t, "type", "SELECT",
                "displayType", "IMAGE", "status", "ACTIVE",
                "values", List.of(value("red", "Đỏ", "/red.png"), value("blue", "Xanh", null))));
        long attrId = created.get("id").asLong();
        assertEquals("IMAGE", created.get("displayType").asText());
        assertEquals("red", created.get("values").get(0).get("code").asText());
        assertEquals("/red.png", created.get("values").get(0).get("imageUrl").asText());
        long redId = created.get("values").get(0).get("id").asLong();
        long blueId = created.get("values").get(1).get("id").asLong();
        assertEquals("Đỏ", created.get("options").get(0).asText());

        // Gán "Đỏ" cho một SKU rồi đổi tên giá trị theo id -> SKU đổi theo, id/code giữ nguyên.
        long catId = createCategory(auth, categoryBody("DM " + t, "DM-" + t, null, null, null)).get("id").asLong();
        long[] ids = createProduct(auth, catId, t, 1_000_000);
        bind(auth, ids, attrId, "Đỏ");

        Map<String, Object> rename = new LinkedHashMap<>();
        rename.put("name", "Màu " + t);
        rename.put("code", "color_" + t);
        rename.put("type", "SELECT");
        Map<String, Object> redRow = new LinkedHashMap<>(value("red", "Đỏ đậm", "/red.png"));
        redRow.put("id", redId);
        Map<String, Object> blueRow = new LinkedHashMap<>(value("blue", "Xanh", null));
        blueRow.put("id", blueId);
        rename.put("values", List.of(redRow, blueRow));
        JsonNode renamed = body(mvc.perform(jsonRequest(put(ATTRIBUTES + "/" + attrId), rename)
                        .header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(redId, renamed.get("values").get(0).get("id").asLong());
        assertEquals("Đỏ đậm", renamed.get("values").get(0).get("value").asText());
        mvc.perform(get(CATALOG + "/" + ids[0] + "/variants/" + ids[1] + "/attributes").header("Authorization", auth))
                .andExpect(jsonPath("$.data[0].value").value("Đỏ đậm"));

        // Đổi mã giá trị đã lưu -> 400; bỏ giá trị đang được SKU dùng -> 409; bỏ giá trị chưa dùng -> OK.
        Map<String, Object> badCode = new LinkedHashMap<>(rename);
        Map<String, Object> redBadCode = new LinkedHashMap<>(redRow);
        redBadCode.put("code", "other");
        badCode.put("values", List.of(redBadCode, blueRow));
        mvc.perform(jsonRequest(put(ATTRIBUTES + "/" + attrId), badCode).header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ATTRIBUTE_VALUE_INVALID"));
        Map<String, Object> dropRed = new LinkedHashMap<>(rename);
        dropRed.put("values", List.of(blueRow));
        mvc.perform(jsonRequest(put(ATTRIBUTES + "/" + attrId), dropRed).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ATTRIBUTE_VALUE_IN_USE"));
        Map<String, Object> dropBlue = new LinkedHashMap<>(rename);
        dropBlue.put("values", List.of(redRow));
        mvc.perform(jsonRequest(put(ATTRIBUTES + "/" + attrId), dropBlue).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values.length()").value(1));

        // Trạng thái + xoá thuộc tính: đang gán cho SKU -> 409.
        mvc.perform(jsonRequest(put(ATTRIBUTES + "/" + attrId), Map.of("name", "Màu " + t, "code", "color_" + t,
                        "type", "SELECT", "status", "INACTIVE")).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.values.length()").value(1)); // không gửi values -> giữ nguyên
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(ATTRIBUTES + "/" + attrId).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ATTRIBUTE_IN_USE"));
    }

    // ------------------------------------------------------------------ Client: filters + lọc

    @Test
    void storefrontFiltersAndAttributeCategoryPriceQueriesReallyFilter() throws Exception {
        String auth = admin();
        String t = tag();
        JsonNode attr = createAttribute(auth, Map.of("name", "Chất liệu " + t, "code", "mat_" + t, "type", "SELECT",
                "values", List.of(value("gold", "Vàng", null), value("silver", "Bạc", null))));
        long attrId = attr.get("id").asLong();
        long goldOption = attr.get("values").get(0).get("id").asLong();

        JsonNode parent = createCategory(auth, categoryBody("Cha " + t, "P-" + t, null, null, null));
        long childCat = createCategory(auth, categoryBody("Con " + t, "C-" + t, parent.get("id").asLong(), null, null))
                .get("id").asLong();
        String parentSlug = parent.get("slug").asText();

        String t1 = tag();
        String t2 = tag();
        long[] gold = createProduct(auth, childCat, t1, 2_000_000);
        long[] silver = createProduct(auth, childCat, t2, 500_000);
        bind(auth, gold, attrId, "Vàng");
        bind(auth, silver, attrId, "Bạc");
        publish(auth, gold[0]);
        publish(auth, silver[0]);

        // Lọc theo danh mục cha lấy cả sản phẩm của danh mục con.
        mvc.perform(get(STORE).param("category", parentSlug))
                .andExpect(jsonPath("$.data.content.length()").value(2));
        mvc.perform(get(STORE).param("categorySlugs", parentSlug + ",khong-ton-tai"))
                .andExpect(jsonPath("$.data.content.length()").value(2));

        // Facets: thuộc tính có 2 giá trị, mỗi giá trị 1 sản phẩm.
        JsonNode filters = body(mvc.perform(get(STORE + "/filters").param("category", parentSlug))
                .andExpect(status().isOk()));
        JsonNode section = null;
        for (JsonNode s : filters) {
            if (s.get("id").asLong() == attrId) {
                section = s;
            }
        }
        assertTrue(section != null, "thiếu nhóm bộ lọc của thuộc tính");
        assertEquals(2, section.get("options").size());
        assertEquals(1, section.get("options").get(0).get("count").asInt());
        assertEquals(String.valueOf(goldOption), section.get("options").get(0).get("value").asText());

        // attributes=<id giá trị> thật sự lọc list/total.
        mvc.perform(get(STORE).param("category", parentSlug).param("attributes", String.valueOf(goldOption)))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("nhan-" + t1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get(STORE).param("category", parentSlug).param("attributes", "999999999"))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(get(STORE).param("attributes", "abc")).andExpect(status().isBadRequest());

        // Lọc giá theo giá hiển thị, inStock, và các sort mới đều chạy.
        mvc.perform(get(STORE).param("category", parentSlug).param("minPrice", "1000000"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("nhan-" + t1));
        mvc.perform(get(STORE).param("category", parentSlug).param("inStock", "true"))
                .andExpect(jsonPath("$.data.content.length()").value(2));
        mvc.perform(get(STORE).param("category", parentSlug).param("sort", "PRICE_ASC"))
                .andExpect(jsonPath("$.data.content[0].slug").value("nhan-" + t2));
        mvc.perform(get(STORE).param("category", parentSlug).param("sort", "NAME_DESC"))
                .andExpect(status().isOk());
        mvc.perform(get(STORE).param("category", parentSlug).param("sort", "BEST_SELLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    // ------------------------------------------------------------------ Vòng đời + lọc admin

    @Test
    void productLifecycleHasUnpublishedAndDiscontinuedAndAdminFiltersWork() throws Exception {
        String auth = admin();
        String t = tag();
        JsonNode cat = createCategory(auth, categoryBody("DM " + t, "DM-" + t, null, null, null));
        String catSlug = cat.get("slug").asText();
        long[] ids = createProduct(auth, cat.get("id").asLong(), t, 1_500_000);
        String base = CATALOG + "/" + ids[0];

        // DRAFT không ngừng kinh doanh được.
        mvc.perform(patch(base + "/discontinue").header("Authorization", auth)).andExpect(status().isConflict());

        publish(auth, ids[0]);
        mvc.perform(patch(base + "/unpublish").header("Authorization", auth))
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));
        mvc.perform(patch(base + "/discontinue").header("Authorization", auth))
                .andExpect(jsonPath("$.data.status").value("DISCONTINUED"));
        // DISCONTINUED phải khôi phục trước khi unpublish.
        mvc.perform(patch(base + "/unpublish").header("Authorization", auth)).andExpect(status().isConflict());
        mvc.perform(get(STORE + "/nhan-" + t)).andExpect(status().isNotFound());

        mvc.perform(get(CATALOG).param("categorySlug", catSlug).param("status", "DISCONTINUED")
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get(CATALOG + "/counts").param("categorySlug", catSlug).header("Authorization", auth))
                .andExpect(jsonPath("$.data.all").value(1))
                .andExpect(jsonPath("$.data.discontinued").value(1))
                .andExpect(jsonPath("$.data.published").value(0));

        mvc.perform(patch(base + "/restore").header("Authorization", auth))
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));
        mvc.perform(patch(base + "/archive").header("Authorization", auth))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mvc.perform(patch(base + "/restore").header("Authorization", auth))
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));
        mvc.perform(patch(base + "/restore").header("Authorization", auth)).andExpect(status().isConflict());

        // Lọc giá + thời gian + status không hợp lệ.
        mvc.perform(get(CATALOG).param("categorySlug", catSlug).param("minPrice", "2000000").header("Authorization", auth))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(get(CATALOG).param("categorySlug", catSlug).param("minPrice", "1000000").param("maxPrice", "1600000")
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get(CATALOG).param("categorySlug", catSlug).param("timeFilter", "TODAY").header("Authorization", auth))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get(CATALOG).param("categorySlug", catSlug).param("timeFilter", "YESTERDAY").header("Authorization", auth))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(get(CATALOG).param("status", "NOPE").header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(get(CATALOG).param("timeFilter", "NOPE").header("Authorization", auth)).andExpect(status().isBadRequest());
    }
}

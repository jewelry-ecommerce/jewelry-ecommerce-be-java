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

/** FE v6 nhóm A: snapshot SKU ở dòng đơn, firstName đầy đủ, DELETE placement, sort sản phẩm phía server, lọc job theo tham chiếu. */
class FeV6GroupAIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;
    private static final String PLACEMENTS = V1 + UrlAdminConstant.Banner.PLACEMENTS;
    private static final String BANNERS = V1 + UrlAdminConstant.Banner.BANNERS;

    private long categoryId(String admin, String t) throws Exception {
        return body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long product(String admin, long categoryId, String t, String suffix) throws Exception {
        long id = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P" + suffix + "-" + t, "name", "Sp" + suffix + " " + t,
                        "slug", "sp" + suffix + "-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU" + suffix + "-" + t, "listPrice", 500_000,
                                "onHand", 20, "sizeLabel", "Size 12", "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + id + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        return id;
    }

    @Test
    void orderLineKeepsSkuSnapshotAfterSkuIsRenamed() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long productId = product(admin, categoryId(admin, t), t, "S");
        JsonNode sku = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin))).get(0);
        long skuId = sku.get("id").asLong();

        String customer = bearer(customerAccessToken());
        String code = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.GUEST), Map.of("recipientName", "Nguyễn Văn An " + t,
                        "phone", "0912345678", "province", "Hà Nội", "addressLine", "2 Lê Lợi", "shippingMethod", "STANDARD",
                        "paymentMethod", "COD", "items", List.of(Map.of("skuId", skuId, "quantity", 1)))))
                .andExpect(status().isCreated())).get("code").asText();

        // Đổi mã SKU sau khi đã đặt đơn.
        Map<String, Object> upd = new HashMap<>();
        upd.put("skuCode", "RENAMED-" + t);
        upd.put("listPrice", 500_000);
        upd.put("onHand", 20);
        upd.put("sizeLabel", "Size 14");
        mvc.perform(jsonRequest(put(CATALOG + "/" + productId + "/variants/" + skuId), upd).header("Authorization", admin))
                .andExpect(status().isOk());

        JsonNode detail = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)).andExpect(status().isOk()));
        JsonNode line = detail.get("items").get(0);
        assertEquals("SKUS-" + t, line.get("skuCode").asText(), "skuCode là snapshot lúc đặt, không đổi theo SKU");
        assertEquals("Size 12", line.get("sizeLabel").asText());

        // Lọc theo mã SKU cũ vẫn ra đơn; mã mới thì không.
        JsonNode old = body(mvc.perform(get(ORDERS).param("skuCode", "skus-" + t).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, old.get("totalElements").asLong());
        JsonNode neu = body(mvc.perform(get(ORDERS).param("skuCode", "renamed-" + t).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(0, neu.get("totalElements").asLong());

        // shippingAddressSnapshot.firstName = họ tên đầy đủ.
        assertEquals("Nguyễn Văn An " + t, detail.get("shippingAddressSnapshot").get("firstName").asText());
    }

    @Test
    void productListSortsOnServer() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long cat = categoryId(admin, t);
        long first = product(admin, cat, t, "A");
        Thread.sleep(20);
        long second = product(admin, cat, t, "B");

        JsonNode asc = body(mvc.perform(get(CATALOG).param("categoryId", String.valueOf(cat)).param("search", t)
                .param("orderBy", "createdAt").param("orderType", "ASC").header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(first, asc.get("content").get(0).get("id").asLong());
        JsonNode desc = body(mvc.perform(get(CATALOG).param("search", t)
                .param("orderBy", "createdAt").param("orderType", "DESC").header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(second, desc.get("content").get(0).get("id").asLong());

        mvc.perform(get(CATALOG).param("orderBy", "name").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get(CATALOG).param("orderBy", "updatedAt").param("orderType", "UP").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get(CATALOG).param("orderBy", "updatedAt").header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void deletePlacementBlockedWhileBannersUseItThenRemoves() throws Exception {
        String admin = bearer(adminAccessToken());
        String code = "DEL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id = body(mvc.perform(jsonRequest(post(PLACEMENTS), Map.of("code", code, "name", "Vị trí xoá"))
                .header("Authorization", admin)).andExpect(status().isOk())).get("id").asLong();

        long bannerId = body(mvc.perform(jsonRequest(post(BANNERS), Map.of("internalName", "B " + code, "placementCode", code,
                        "mediaUrl", "/a.jpg", "status", "ACTIVE")).header("Authorization", admin))).get("id").asLong();

        // Còn banner dùng -> 409 PLACEMENT_IN_USE, vị trí còn nguyên.
        mvc.perform(delete(PLACEMENTS + "/" + id).header("Authorization", admin)).andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors[0].code").value("PLACEMENT_IN_USE"));

        // Xoá banner -> xoá được vị trí; lần 2 -> 404.
        mvc.perform(delete(BANNERS + "/" + bannerId).header("Authorization", admin)).andExpect(status().isOk());
        JsonNode ok = body(mvc.perform(delete(PLACEMENTS + "/" + id).header("Authorization", admin)).andExpect(status().isOk()));
        assertTrue(ok.get("success").asBoolean());
        mvc.perform(delete(PLACEMENTS + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get(PLACEMENTS).header("Authorization", admin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[?(@.code=='" + code + "')]").doesNotExist());
    }

    @Test
    void createdByIdStoredReturnedAndFilterable() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long cat = categoryId(admin, t);
        long productId = product(admin, cat, t, "U");
        JsonNode detail = body(mvc.perform(get(CATALOG + "/" + productId).header("Authorization", admin)).andExpect(status().isOk()));
        long adminId = detail.get("createdById").asLong();
        assertTrue(adminId > 0, "sản phẩm lưu người tạo");

        JsonNode mine = body(mvc.perform(get(CATALOG).param("search", t).param("createdById", String.valueOf(adminId))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, mine.get("totalElements").asLong());
        JsonNode other = body(mvc.perform(get(CATALOG).param("search", t).param("createdById", String.valueOf(adminId + 99999))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(0, other.get("totalElements").asLong());
        JsonNode counts = body(mvc.perform(get(CATALOG + "/counts").param("search", t).param("createdById", String.valueOf(adminId + 99999))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(0, counts.get("all").asLong());

        // Đơn: khách đăng nhập -> createdById = id khách; khách vãng lai -> không có.
        JsonNode sku = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin))).get(0);
        long skuId = sku.get("id").asLong();
        String customer = bearer(customerAccessToken());
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "Hồ Chí Minh", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
        String code = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", addressId,
                        "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", 1)))).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();
        JsonNode order = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)).andExpect(status().isOk()));
        long customerId = order.get("customerId").asLong();
        assertEquals(customerId, order.get("createdById").asLong());
        JsonNode filtered = body(mvc.perform(get(ORDERS).param("skuCode", "SKUU-" + t).param("createdById", String.valueOf(customerId))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, filtered.get("totalElements").asLong());
        JsonNode none = body(mvc.perform(get(ORDERS).param("skuCode", "SKUU-" + t).param("createdById", String.valueOf(customerId + 99999))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(0, none.get("totalElements").asLong());
        JsonNode oc = body(mvc.perform(get(ORDERS + "/counts").param("skuCode", "SKUU-" + t).param("createdById", String.valueOf(customerId))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, oc.get("total").asLong());
    }
}

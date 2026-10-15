package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE bổ sung: lọc SKU/phương thức giao/tỉnh, sort, dateField=deliveredAt, /orders/counts, tra đơn theo id số, field mới, stockStatus. */
class OmsOrderListExtrasIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    private JsonNode getJson(String url, String admin, String... kv) throws Exception {
        var req = get(url).header("Authorization", admin);
        for (int i = 0; i < kv.length; i += 2) {
            req = req.param(kv[i], kv[i + 1]);
        }
        return body(mvc.perform(req).andExpect(status().isOk()));
    }

    private static boolean has(JsonNode page, String code) {
        for (JsonNode o : page.get("content")) {
            if (code.equals(o.get("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private long product(String admin, long categoryId, String t, String suffix, int onHand) throws Exception {
        long id = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P" + suffix + "-" + t, "name", "Sp" + suffix + " " + t,
                        "slug", "sp" + suffix + "-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU" + suffix + "-" + t, "listPrice", 500_000,
                                "onHand", onHand, "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + id + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        return id;
    }

    @Test
    void orderFiltersSortCountsAndNewFields() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = product(admin, categoryId, t, "A", 20);
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();

        String customer = bearer(customerAccessToken());
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "Hồ Chí Minh", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
        String customerOrder = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", addressId,
                        "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", 1)))).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();
        String guestOrder = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.GUEST), Map.of("recipientName", "Khách " + t,
                        "phone", "0912345678", "province", "Hà Nội", "addressLine", "2 Lê Lợi", "shippingMethod", "EXPRESS",
                        "paymentMethod", "COD", "items", List.of(Map.of("skuId", skuId, "quantity", 1)))))
                .andExpect(status().isCreated())).get("code").asText();

        // --- skuCode (một phần, không phân biệt hoa/thường) ---
        JsonNode bySku = getJson(ORDERS, admin, "skuCode", ("skuA-" + t).toLowerCase());
        assertTrue(has(bySku, customerOrder) && has(bySku, guestOrder));
        assertEquals(0, getJson(ORDERS, admin, "skuCode", "KHONG-CO-SKU-NAY-" + t).get("totalElements").asLong());

        // --- shippingMethod: Standard (FE) = STANDARD (BE) ---
        JsonNode std = getJson(ORDERS, admin, "shippingMethod", "Standard", "skuCode", "skuA-" + t);
        assertTrue(has(std, customerOrder) && !has(std, guestOrder));
        JsonNode exp = getJson(ORDERS, admin, "shippingMethod", "express", "skuCode", "skuA-" + t);
        assertTrue(has(exp, guestOrder) && !has(exp, customerOrder));

        // --- provinceName ---
        JsonNode hcm = getJson(ORDERS, admin, "provinceName", "hồ chí", "skuCode", "skuA-" + t);
        assertTrue(has(hcm, customerOrder) && !has(hcm, guestOrder));
        assertEquals(0, getJson(ORDERS, admin, "provinceName", "Cà Mau", "skuCode", "skuA-" + t).get("totalElements").asLong());

        // --- source ---
        assertTrue(has(getJson(ORDERS, admin, "source", "WEBSITE", "skuCode", "skuA-" + t), customerOrder));
        assertEquals(0, getJson(ORDERS, admin, "source", "POS", "skuCode", "skuA-" + t).get("totalElements").asLong());

        // --- sort theo orderCode ---
        JsonNode asc = getJson(ORDERS, admin, "orderBy", "orderCode", "orderType", "ASC", "skuCode", "skuA-" + t);
        JsonNode desc = getJson(ORDERS, admin, "orderBy", "orderCode", "orderType", "DESC", "skuCode", "skuA-" + t);
        assertEquals(2, asc.get("content").size());
        assertEquals(asc.get("content").get(0).get("code").asText(), desc.get("content").get(1).get("code").asText());
        assertTrue(asc.get("content").get(0).get("code").asText().compareTo(asc.get("content").get(1).get("code").asText()) < 0);
        // sort theo grandTotal: EXPRESS đắt hơn STANDARD (hoặc bằng) -> ASC không bao giờ đảo
        JsonNode byTotal = getJson(ORDERS, admin, "orderBy", "grandTotal", "orderType", "ASC", "skuCode", "skuA-" + t);
        assertTrue(byTotal.get("content").get(0).get("grandTotal").decimalValue()
                .compareTo(byTotal.get("content").get(1).get("grandTotal").decimalValue()) <= 0);
        mvc.perform(get(ORDERS).header("Authorization", admin).param("orderBy", "hack")).andExpect(status().isBadRequest());
        mvc.perform(get(ORDERS).header("Authorization", admin).param("orderType", "SIDEWAYS")).andExpect(status().isBadRequest());
        mvc.perform(get(ORDERS).header("Authorization", admin).param("dateField", "birthday")).andExpect(status().isBadRequest());

        // --- counts: cùng bộ lọc, mọi trạng thái đều có khoá ---
        JsonNode c0 = getJson(ORDERS + "/counts", admin, "skuCode", "skuA-" + t);
        assertEquals(2, c0.get("total").asLong());
        assertEquals(2, c0.get("byStatus").get("PENDING").asLong());
        for (String st : List.of("CONFIRMED", "SHIPPING", "DELIVERED", "CANCELLED", "RETURNED")) {
            assertEquals(0, c0.get("byStatus").get(st).asLong(), st);
        }
        // counts bỏ qua status trong query (FE gửi cả tab đang chọn) và không bị 404 bởi /{orderCode}
        assertEquals(2, getJson(ORDERS + "/counts", admin, "skuCode", "skuA-" + t, "status", "PENDING").get("total").asLong());

        // --- giao hàng: PENDING -> CONFIRMED -> SHIPPING -> DELIVERED ---
        for (String next : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + customerOrder + "/status"), Map.of("status", next))
                    .header("Authorization", admin)).andExpect(status().isOk());
        }
        JsonNode c1 = getJson(ORDERS + "/counts", admin, "skuCode", "skuA-" + t);
        assertEquals(1, c1.get("byStatus").get("PENDING").asLong());
        assertEquals(1, c1.get("byStatus").get("DELIVERED").asLong());
        assertEquals(2, c1.get("total").asLong());

        // --- dateField=deliveredAt ---
        String today = LocalDate.now().toString();
        JsonNode delivered = getJson(ORDERS, admin, "dateField", "deliveredAt", "fromDate", today, "toDate", today,
                "skuCode", "skuA-" + t);
        assertTrue(has(delivered, customerOrder) && !has(delivered, guestOrder), "đơn chưa giao không có deliveredAt");
        assertTrue(has(getJson(ORDERS, admin, "dateField", "createdAt", "fromDate", today, "toDate", today,
                "skuCode", "skuA-" + t), guestOrder));

        // --- field mới + tra theo id số ---
        JsonNode detail = getJson(ORDERS + "/" + customerOrder, admin);
        assertNotNull(detail.get("customerId"));
        assertFalse(detail.get("customerId").isNull());
        assertFalse(detail.get("deliveredAt").isNull());
        assertEquals(detail.get("grandTotal").decimalValue().compareTo(detail.get("codAmount").decimalValue()), 0);
        JsonNode guestDetail = getJson(ORDERS + "/" + guestOrder, admin);
        assertTrue(guestDetail.get("customerId") == null || guestDetail.get("customerId").isNull(), "khách vãng lai không có customerId");
        assertTrue(guestDetail.get("deliveredAt") == null || guestDetail.get("deliveredAt").isNull(), "chưa giao thì không có deliveredAt");
        long id = detail.get("id").asLong();
        assertEquals(customerOrder, getJson(ORDERS + "/" + id, admin).get("code").asText());
        mvc.perform(get(ORDERS + "/999999999").header("Authorization", admin)).andExpect(status().isNotFound());
        // thao tác theo id số cũng được (huỷ đơn guest bằng id)
        long guestId = guestDetail.get("id").asLong();
        mvc.perform(post(ORDERS + "/" + guestId + "/cancel").header("Authorization", admin)).andExpect(status().isOk());
        assertEquals("CANCELLED", getJson(ORDERS + "/" + guestOrder, admin).get("status").asText());
    }

    @Test
    void productStockStatusFilter() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DMS " + t, "slug", "dms-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long plenty = product(admin, categoryId, t, "P", 20);
        long low = product(admin, categoryId, t, "L", 3);
        long none = product(admin, categoryId, t, "O", 0);
        String cat = "dms-" + t;
        assertEquals(List.of(plenty), ids(getJson(CATALOG, admin, "categorySlug", cat, "stockStatus", "IN_STOCK")));
        assertEquals(List.of(low), ids(getJson(CATALOG, admin, "categorySlug", cat, "stockStatus", "low_stock")));
        assertEquals(List.of(none), ids(getJson(CATALOG, admin, "categorySlug", cat, "stockStatus", "OUT_OF_STOCK")));
        assertEquals(3, getJson(CATALOG, admin, "categorySlug", cat, "stockStatus", "").get("totalElements").asLong());
        mvc.perform(get(CATALOG).header("Authorization", admin).param("stockStatus", "MAYBE")).andExpect(status().isBadRequest());
    }

    private static List<Long> ids(JsonNode page) {
        java.util.ArrayList<Long> out = new java.util.ArrayList<>();
        page.get("content").forEach(p -> out.add(p.get("id").asLong()));
        return out;
    }
}

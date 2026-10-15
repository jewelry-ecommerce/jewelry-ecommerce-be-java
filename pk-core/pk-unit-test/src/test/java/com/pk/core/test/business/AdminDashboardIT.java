package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dashboard admin: số liệu tổng hợp từ DB thật (đơn đã xác nhận, tồn thấp, top sản phẩm) + phân quyền + range sai. */
class AdminDashboardIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;
    private static final String DASH = V1 + UrlAdminConstant.Dashboard.OVERVIEW;

    private JsonNode kpi(JsonNode overview, String key) {
        for (JsonNode k : overview.get("kpis")) {
            if (key.equals(k.get("key").asText())) {
                return k;
            }
        }
        throw new AssertionError("không có KPI " + key);
    }

    @Test
    void overviewAggregatesRealOrdersAndStock() throws Exception {
        String admin = bearer(adminAccessToken());
        JsonNode before = body(mvc.perform(get(DASH).header("Authorization", admin)).andExpect(status().isOk()));
        double revBefore = kpi(before, "net_revenue").get("value").asDouble();
        double ordersBefore = kpi(before, "order_count").get("value").asDouble();

        String t = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 700_000, "onHand", 4,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        String customer = bearer(customerAccessToken());
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
        JsonNode order = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", addressId,
                        "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", 2)))).header("Authorization", customer))
                .andExpect(status().isCreated()));
        String code = order.get("code").asText();
        double grand = order.get("grandTotal").asDouble();

        // Đơn PENDING chưa tính doanh thu nhưng đã nằm trong ordersByStatus & recentOrders.
        JsonNode pending = body(mvc.perform(get(DASH).header("Authorization", admin)));
        assertEquals(revBefore, kpi(pending, "net_revenue").get("value").asDouble());
        assertTrue(pending.get("recentOrders").get(0).get("code").asText().length() > 0);

        mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", "CONFIRMED")).header("Authorization", admin))
                .andExpect(status().isOk());
        JsonNode after = body(mvc.perform(get(DASH).header("Authorization", admin)));
        assertEquals(revBefore + grand, kpi(after, "net_revenue").get("value").asDouble(), 0.5);
        assertEquals(ordersBefore + 1, kpi(after, "order_count").get("value").asDouble(), 0.001);

        // Chuỗi doanh thu: nhãn và hai series cùng độ dài; tổng series Revenue = KPI.
        JsonNode rev = after.get("revenue");
        assertEquals(rev.get("labels").size(), rev.get("series").get(0).get("values").size());
        double sum = 0;
        for (JsonNode v : rev.get("series").get(0).get("values")) {
            sum += v.asDouble();
        }
        assertEquals(kpi(after, "net_revenue").get("value").asDouble(), sum, 0.5);

        // Top sản phẩm có sản phẩm vừa bán (2 cái, đúng doanh thu dòng) + tên danh mục thật.
        JsonNode tops = body(mvc.perform(get(V1 + UrlAdminConstant.Dashboard.TOP_PRODUCTS).header("Authorization", admin)));
        boolean found = false;
        for (JsonNode p : tops) {
            if (String.valueOf(productId).equals(p.get("productId").asText())) {
                found = true;
                assertEquals(2, p.get("unitsSold").asInt());
                assertEquals(1_400_000, p.get("revenue").asDouble(), 0.5);
                assertEquals("DM " + t, p.get("categoryName").asText());
            }
        }
        assertTrue(found, "top sản phẩm phải có sản phẩm vừa bán");

        // Tồn: onHand 4, giữ 2 => khả dụng 2 <= ngưỡng 5 nên nằm trong cảnh báo tồn thấp.
        JsonNode low = body(mvc.perform(get(V1 + UrlAdminConstant.Dashboard.LOW_STOCK).header("Authorization", admin)));
        boolean lowFound = false;
        for (JsonNode r : low) {
            lowFound |= ("SKU-" + t).equals(r.get("skuCode").asText());
        }
        // Danh sách tối đa 8 dòng nên chỉ khẳng định khi chưa đầy.
        assertTrue(lowFound || low.size() == 8);

        // Các range hợp lệ đều 200; sai range -> 400 (không 201/500).
        for (String r : List.of("LAST_7_DAYS", "LAST_30_DAYS", "LAST_12_MONTHS")) {
            mvc.perform(get(DASH).param("range", r).header("Authorization", admin)).andExpect(status().isOk());
        }
        mvc.perform(get(DASH).param("range", "FOREVER").header("Authorization", admin)).andExpect(status().isBadRequest());
        JsonNode recent = body(mvc.perform(get(V1 + UrlAdminConstant.Dashboard.RECENT_ORDERS).param("limit", "3")
                .header("Authorization", admin)));
        assertTrue(recent.size() <= 3 && recent.size() >= 1);
    }

    @Test
    void dashboardRequiresAdminRole() throws Exception {
        mvc.perform(get(DASH)).andExpect(status().isUnauthorized());
        mvc.perform(get(DASH).header("Authorization", bearer(customerAccessToken()))).andExpect(status().isForbidden());
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khách tạo / huỷ yêu cầu đổi trả trên storefront (đồ án). */
class StorefrontOrderReturnIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;
    private static final String RETURNS = V1 + UrlConstant.OrderReturnClient.BASE;

    @Test
    void customerCanCreateAndCancelReturnOnDeliveredOrder() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        String customer = bearer(customerAccessToken());
        String skuCode = "SKU-R-" + t;

        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-r-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-R-" + t, "name", "Nhẫn " + t, "slug", "nhan-r-" + t,
                        "variants", List.of(Map.of("skuCode", skuCode, "listPrice", 500_000, "onHand", 3,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of(
                        "recipientName", "Nguyễn A", "phone", "0901234567", "province", "HCM", "district", "1442",
                        "ward", "21211", "addressLine", "1 Lê Lợi")).header("Authorization", customer))
                .andExpect(status().isCreated())).get("id").asLong();

        String code = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of(
                        "addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", 1)))).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();

        for (String st : List.of("CONFIRMED", "SHIPPING", "DELIVERED")) {
            mvc.perform(jsonRequest(patch(ORDERS + "/" + code + "/status"), Map.of("status", st))
                            .header("Authorization", admin))
                    .andExpect(status().isOk());
        }

        long lineId = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)))
                .get("items").get(0).get("id").asLong();

        JsonNode created = body(mvc.perform(jsonRequest(post(RETURNS), Map.of(
                        "orderId", code,
                        "type", "Refund",
                        "reason", "ChangedMind",
                        "returnItems", List.of(Map.of("orderItemId", lineId, "quantity", 1)),
                        "exchangeItems", List.of(),
                        "note", "Không ưng"))
                .header("Authorization", customer))
                .andExpect(status().isCreated()));

        assertNotNull(created.get("returnCode").asText());
        assertEquals("Pending", created.get("status").asText());
        String returnCode = created.get("returnCode").asText();

        mvc.perform(put(RETURNS + "/by-code/" + returnCode + "/cancel").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Cancelled"));
    }
}

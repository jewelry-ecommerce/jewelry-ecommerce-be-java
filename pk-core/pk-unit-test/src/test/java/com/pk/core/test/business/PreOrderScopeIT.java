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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Đặt trước có phạm vi riêng + thứ tự nhãn PRE_ORDER -> OUT_OF_STOCK -> CAMPAIGN. Dữ liệu tạo qua API. */
class PreOrderScopeIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String PRE_ORDER = V1 + UrlAdminConstant.PreOrder.BASE;

    private String admin;
    private long categoryId;
    private String t;

    /** [productId, skuId]. */
    private long[] product(String tag, int onHand) throws Exception {
        long pid = body(mvc.perform(jsonRequest(post(CATALOG), Map.of("categoryId", categoryId, "code", "P-" + tag + t,
                "name", "SP " + tag + t, "slug", "sp-" + tag.toLowerCase() + t,
                "variants", List.of(Map.of("skuCode", "SKU-" + tag + t, "listPrice", 1_000_000, "onHand", onHand,
                        "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long sid = body(mvc.perform(get(CATALOG + "/" + pid + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + pid + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        return new long[] {pid, sid};
    }

    private void template(String badgeType) throws Exception {
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.TEMPLATES), Map.of("name", badgeType + " " + t,
                "code", badgeType + "_" + t, "type", "TEXT", "badgeType", badgeType, "status", "ACTIVE",
                "displayText", badgeType, "defaultPosition", "TOP_LEFT", "defaultPriorityWeight", 10))
                .header("Authorization", admin)).andExpect(status().isOk());
    }

    private String badgeType(long skuId) throws Exception {
        JsonNode badges = body(mvc.perform(get(V1 + UrlConstant.Badge.BASE).param("skuIds", String.valueOf(skuId)))
                .andExpect(status().isOk())).get(0).get("badges");
        return badges.size() == 0 ? null : badges.get(0).get("badgeType").asText();
    }

    private void setPreOrder(Map<String, Object> req) throws Exception {
        mvc.perform(jsonRequest(post(PRE_ORDER), req).header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void scopedPreOrderLabelsAndAllowsOnlyConfiguredSkus() throws Exception {
        t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        admin = bearer(adminAccessToken());
        categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t.toLowerCase(), "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        template("PRE_ORDER");
        template("OUT_OF_STOCK");
        template("CAMPAIGN");
        long[] inStockInScope = product("A", 3);
        long[] soldOutInScope = product("B", 0);
        long[] soldOutOutside = product("C", 0);
        String token = customerAccessToken();
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", bearer(token))).andExpect(status().isCreated())).get("id").asLong();
        try {
            // Validation cấu hình.
            for (Map<String, Object> bad : List.of(
                    Map.<String, Object>of("enabled", true, "scopeType", "ALL", "scopeIds", List.of(1)),
                    Map.<String, Object>of("enabled", true, "scopeType", "SKU"),
                    Map.<String, Object>of("enabled", true, "scopeType", "SKU", "scopeIds", List.of(0)),
                    Map.<String, Object>of("enabled", true, "scopeType", "BRAND", "scopeIds", List.of(1)))) {
                mvc.perform(jsonRequest(post(PRE_ORDER), bad).header("Authorization", admin)).andExpect(status().isBadRequest());
            }

            // Phạm vi riêng: chỉ sản phẩm A, B.
            setPreOrder(Map.of("enabled", true, "message", "Giao sau 2 tuần", "scopeType", "PRODUCT",
                    "scopeIds", List.of(inStockInScope[0], soldOutInScope[0])));
            mvc.perform(get(V1 + UrlConstant.PreOrder.CURRENT)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scopeType").value("PRODUCT"))
                    .andExpect(jsonPath("$.data.scopeIds.length()").value(2));
            assertEquals("PRE_ORDER", badgeType(inStockInScope[1]));   // còn hàng nhưng thuộc cấu hình đặt trước
            assertEquals("PRE_ORDER", badgeType(soldOutInScope[1]));
            assertEquals("OUT_OF_STOCK", badgeType(soldOutOutside[1])); // hết hàng ngoài phạm vi: không phải đặt trước

            Map<String, Object> orderB = Map.of("addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                    "items", List.of(Map.of("skuId", soldOutInScope[1], "quantity", 1)));
            Map<String, Object> orderC = Map.of("addressId", addressId, "shippingMethod", "STANDARD", "paymentMethod", "COD",
                    "items", List.of(Map.of("skuId", soldOutOutside[1], "quantity", 1)));
            mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), orderC).header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest());
            mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), orderB).header("Authorization", bearer(token)))
                    .andExpect(status().isCreated());

            // Cấu hình ALL (cũ): mọi SKU HẾT hàng đặt trước được; SKU còn hàng không có nhãn PRE_ORDER.
            setPreOrder(Map.of("enabled", true));
            assertEquals("PRE_ORDER", badgeType(soldOutOutside[1]));
            assertFalse("PRE_ORDER".equals(badgeType(inStockInScope[1])));
            mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), orderC).header("Authorization", bearer(token)))
                    .andExpect(status().isCreated());

            // Tắt: hết hàng lại là OUT_OF_STOCK, không đặt được.
            setPreOrder(Map.of("enabled", false));
            assertEquals("OUT_OF_STOCK", badgeType(soldOutOutside[1]));
        } finally {
            setPreOrder(Map.of("enabled", false));
        }
    }
}

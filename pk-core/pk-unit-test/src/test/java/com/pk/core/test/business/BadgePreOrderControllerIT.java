package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin badge-templates/badge-flow theo spec + nhãn storefront theo quy tắc PRE_ORDER -> OUT_OF_STOCK -> CAMPAIGN và
 * luồng đặt trước. DB test dùng chung nên mọi mã sinh ngẫu nhiên và mẫu của test được GHIM để luôn thắng. */
class BadgePreOrderControllerIT extends IntegrationTestBase {

    @Autowired CategoryRepo categories;
    @Autowired ProductRepo products;
    @Autowired ProductSkuRepo productSkus;

    private String adminAuth() throws Exception {
        return bearer(adminAccessToken());
    }

    private ResultActions adminPost(String path, Object body) throws Exception {
        return mvc.perform(jsonRequest(post(path), body).header("Authorization", adminAuth()));
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String createTemplate(String badgeType, String displayText) throws Exception {
        String code = badgeType + "_" + tag();
        return body(adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES, Map.of("name", "Mẫu " + code, "code", code,
                "type", "TEXT", "badgeType", badgeType, "status", "ACTIVE", "displayText", displayText,
                "defaultPosition", "TOP_LEFT",
                "styleConfig", Map.of("shape", "PILL", "backgroundColor", "#B8860B", "textColor", "#FFFFFF", "fontSize", 12),
                "defaultPriorityWeight", 10))
                .andExpect(status().isOk())).get("id").asText();
    }

    private void createPinnedFlow(String ruleType, String... templateIds) throws Exception {
        List<Map<String, Object>> refs = new java.util.ArrayList<>();
        for (String id : templateIds) {
            refs.add(Map.of("badgeTemplateId", id, "priorityWeight", 999, "isPinned", true));
        }
        adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.FLOW, Map.of("name", "Flow " + tag(), "status", "ACTIVE", "ruleType", ruleType,
                "channel", "ALL", "templates", refs)).andExpect(status().isOk());
    }

    /** [productId, skuId]. */
    private long[] newProduct(int onHand) {
        String tag = tag();
        CategoryEntity category = new CategoryEntity("DM " + tag, "dm-" + tag, null);
        categories.create(category);
        ProductEntity product = new ProductEntity(category.getId(), "P-" + tag, "SP " + tag, "sp-" + tag);
        product.setBasePrice(BigDecimal.valueOf(1_000_000));
        product.publish();
        products.create(product);
        ProductSkuEntity sku = new ProductSkuEntity("SKU-" + tag, "M", BigDecimal.valueOf(1_000_000), onHand);
        sku.setProductId(product.getId());
        sku.setName("Nhẫn " + tag);
        sku.setStatus(ProductSkuEntity.PUBLISHED);
        sku.setDefault(true);
        productSkus.create(sku);
        return new long[] {product.getId(), sku.getId()};
    }

    private void setPreOrder(boolean enabled) throws Exception {
        adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.PreOrder.BASE, Map.of("enabled", enabled, "message", "Giao sau 2-3 tuần"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ admin templates

    @Test
    void patchTemplateCanActivateAndUpdateAssetFields() throws Exception {
        String code = "ACT_" + tag();
        String id = body(adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES, Map.of("name", "Chờ bật", "code", code,
                "type", "TEXT", "badgeType", "NEW_ARRIVAL", "displayText", "Mới"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))).get("id").asText();
        mvc.perform(jsonRequest(patch(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES + "/" + id),
                        Map.of("status", "ACTIVE", "icon", "/i.svg", "iconMobile", "/im.svg", "imageMobile", "/m.png",
                                "assetMeta", Map.of("w", 24), "defaultPriorityWeight", 7))
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.icon").value("/i.svg"))
                .andExpect(jsonPath("$.data.defaultPriorityWeight").value(7));
        mvc.perform(jsonRequest(patch(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES + "/" + id),
                        Map.of("status", "NOPE")).header("Authorization", adminAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void templateCrudFollowsSpec() throws Exception {
        String code = "NEW_" + tag();
        JsonNode created = body(adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES, Map.of("name", "Hàng mới", "code", code,
                "type", "TEXT", "badgeType", "NEW_ARRIVAL", "displayText", "Mới"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.defaultPosition").value("TOP_LEFT")));
        String id = created.get("id").asText();

        adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES, Map.of("name", "Trùng", "code", code, "type", "TEXT",
                "badgeType", "NEW_ARRIVAL")).andExpect(status().isConflict());
        adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES, Map.of("name", "Sai enum", "code", "X_" + tag(), "type", "NOPE",
                "badgeType", "NEW_ARRIVAL")).andExpect(status().isBadRequest());

        mvc.perform(jsonRequest(patch(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES + "/" + id),
                        Map.of("displayText", "Mới ra mắt", "defaultPosition", "TOP_RIGHT", "code", "IGNORED"))
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayText").value("Mới ra mắt"))
                .andExpect(jsonPath("$.data.defaultPosition").value("TOP_RIGHT"))
                .andExpect(jsonPath("$.data.code").value(code));

        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES).param("badgeType", "NEW_ARRIVAL").param("take", "100")
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + id + "')]").exists())
                .andExpect(jsonPath("$.data.page").value(1));

        mvc.perform(delete(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES + "/" + id).header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.TEMPLATES + "/" + id).header("Authorization", adminAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void flowCreateListAndReplace() throws Exception {
        String templateId = createTemplate("CAMPAIGN", "Sale");
        JsonNode flow = body(adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.FLOW, Map.of("name", "Flow mùa hè", "status", "ACTIVE",
                "ruleType", "ALL", "channel", "WEB",
                "templates", List.of(Map.of("badgeTemplateId", templateId, "priorityWeight", 10, "isPinned", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.templates[0].badgeTemplateId").value(templateId)));
        String flowId = flow.get("id").asText();

        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.FLOW).param("status", "ACTIVE").param("take", "100")
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + flowId + "')]").exists())
                .andExpect(jsonPath("$.data.counts.all").isNumber())
                .andExpect(jsonPath("$.data.counts.active").isNumber());

        mvc.perform(jsonRequest(put(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.FLOW + "/" + flowId), Map.of("name", "Flow đã sửa",
                        "status", "INACTIVE", "ruleType", "ALL",
                        "templates", List.of(Map.of("badgeTemplateId", templateId))))
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Flow đã sửa"))
                .andExpect(jsonPath("$.data.isActive").value(false));

        adminPost(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Badge.FLOW, Map.of("name", "Sai", "ruleType", "ALL",
                "templates", List.of(Map.of("badgeTemplateId", "999999999")))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ storefront + pre-order

    @Test
    void outOfStockBadgeSwitchesToPreOrderAndOrderBecomesPossible() throws Exception {
        String preOrderTemplate = createTemplate("PRE_ORDER", "Đặt trước");
        String oosTemplate = createTemplate("OUT_OF_STOCK", "Hết hàng");
        createPinnedFlow("OUT_OF_STOCK", preOrderTemplate, oosTemplate);
        long[] p = newProduct(0);

        try {
            setPreOrder(false);
            mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Badge.BASE).param("skuIds", String.valueOf(p[1])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].skuId").value(String.valueOf(p[1])))
                    .andExpect(jsonPath("$.data[0].badges.length()").value(1))
                    .andExpect(jsonPath("$.data[0].badges[0].badgeType").value("OUT_OF_STOCK"));

            // Chưa bật đặt trước: không đặt được hàng hết kho.
            String token = customerAccessToken();
            long addressId = createAddress(token);
            Map<String, Object> order = Map.of("addressId", addressId, "shippingMethod", "STANDARD",
                    "paymentMethod", "COD", "items", List.of(Map.of("skuId", p[1], "quantity", 1)));
            mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Order.BASE), order).header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest());

            setPreOrder(true);
            mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Badge.BASE).param("skuIds", String.valueOf(p[1])))
                    .andExpect(jsonPath("$.data[0].badges[0].badgeType").value("PRE_ORDER"));
            mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.PreOrder.CURRENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").value(true));

            mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Order.BASE), order).header("Authorization", bearer(token)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.grandTotal").value(1_000_000));
        } finally {
            setPreOrder(false);
        }
    }

    @Test
    void inStockSkuGetsCampaignBadgeAndUnknownSkuIsOmitted() throws Exception {
        String saleTemplate = createTemplate("CAMPAIGN", "Giảm giá");
        createPinnedFlow("ALL", saleTemplate);
        long[] p = newProduct(5);

        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Badge.BASE).param("skuIds", String.valueOf(p[1])).param("skuIds", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].badges[0].id").value(saleTemplate))
                .andExpect(jsonPath("$.data[0].badges[0].styleConfig.shape").value("PILL"));
    }

    private long createAddress(String token) throws Exception {
        String addressBody = json.writeValueAsString(Map.of("recipientName", "Nguyễn A", "phone", "0901234567",
                "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"));
        return json.readTree(mvc.perform(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Me.ADDRESSES).header("Authorization", bearer(token))
                        .contentType("application/json").content(addressBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }
}

package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wizard chiến dịch khuyến mãi: nháp -> schema -> thành phần -> voucher -> mô phỏng -> phát hành -> engine giá thật -> vòng đời. */
class PromotionCampaignIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String DRAFTS = V1 + UrlAdminConstant.Promotion.DRAFTS;
    private static final String PUBLISHED = V1 + UrlAdminConstant.Promotion.PUBLISHED;
    private static final String CAMPAIGNS = V1 + UrlAdminConstant.Promotion.CAMPAIGNS;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;

    @Autowired PromotionPricingService pricing;

    private String admin;

    private JsonNode ok(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return body(r.andExpect(status().isOk()));
    }

    private String errCode(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return body(r).get("errors").get(0).get("code").asText();
    }

    private long[] product(String t, String s, int price) throws Exception {
        long cat = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM" + s + t, "slug", "dm" + s + "-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long id = body(mvc.perform(jsonRequest(post(CATALOG), Map.of("categoryId", cat, "code", "PC" + s + "-" + t, "name", "KM " + s + t,
                "slug", "km" + s + "-" + t, "variants", List.of(Map.of("skuCode", "KM" + s + "-" + t, "listPrice", price, "onHand", 9,
                        "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
        long sku = body(mvc.perform(get(CATALOG + "/" + id + "/variants").header("Authorization", admin))).get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + id + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        return new long[]{id, sku};
    }

    private Map<String, Object> line(String id, long sku, int qty, int unit) {
        return Map.of("lineId", id, "skuId", String.valueOf(sku), "quantity", qty, "unitPriceMinor", String.valueOf(unit),
                "lineSubtotalMinor", String.valueOf(qty * unit), "categoryIds", List.of(), "collectionIds", List.of());
    }

    @Test
    void wizardFromDraftToPublishedAndEngine() throws Exception {
        admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        long[] a = product(t, "A", 100_000);
        long[] b = product(t, "B", 50_000);
        Instant now = Instant.now();

        // ---- nháp: validate + tạo
        mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "", "startsAt", now.toString(), "endsAt", now.plus(1, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "X", "startsAt", now.toString(), "endsAt", now.minus(1, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        JsonNode draft = body(mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "Chiến dịch " + t, "description", "mô tả",
                        "startsAt", now.minus(1, ChronoUnit.HOURS).toString(), "endsAt", now.plus(7, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isCreated()));
        String d = draft.get("id").asText();
        assertEquals("EDITING", draft.get("status").asText());

        // ---- schema 1: MARKDOWN (điều kiện ngầm)
        JsonNode s1 = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "Giảm 20%", "schemaType", "MARKDOWN",
                "costSources", List.of("ECOMMERCE"), "conditionMode", "IMPLICIT_ALWAYS")).header("Authorization", admin)).andExpect(status().isCreated()));
        String sid1 = s1.get("id").asText();
        assertEquals("SKU_ITEM", s1.get("scope").asText());
        assertEquals("DRAFT", s1.get("displayStatus").asText());
        JsonNode cond1 = ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + sid1 + "/condition-config-options").header("Authorization", admin)));
        assertEquals(1, cond1.size());
        assertEquals("ALWAYS", cond1.get(0).get("configType").asText());
        assertEquals("2.0.0", cond1.get(0).get("authoringContractVersion").asText());
        JsonNode ben1 = ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + sid1 + "/benefit-config-options").header("Authorization", admin)));
        assertEquals("MARKDOWN", ben1.get(0).get("configType").asText());
        assertTrue(ben1.get(0).get("configSchema").get("properties").has("discountValue"));
        assertEquals("ALWAYS", ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + sid1 + "/components").header("Authorization", admin)))
                .get("condition").get("config").get("type").asText());

        // benefit sai -> 400; đúng -> lưu kèm authoringValues
        Map<String, Object> bad = Map.of("configType", "MARKDOWN", "configVersion", "1.0.0", "values", Map.of("discountKind", "PERCENT", "discountValue", 150));
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid1 + "/benefit"), bad).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid1 + "/benefit"),
                Map.of("configType", "FLAT_PRICE", "configVersion", "1.0.0", "values", Map.of("fixedPriceAmountMinor", 1000))).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        JsonNode saved = body(mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid1 + "/benefit"),
                Map.of("configType", "MARKDOWN", "configVersion", "1.0.0",
                        "values", Map.of("discountKind", "PERCENT", "discountValue", 20, "maxDiscountAmountMinor", 15000))).header("Authorization", admin))
                .andExpect(status().isOk()));
        assertEquals("MARKDOWN", saved.get("config").get("type").asText());
        assertEquals(15000, saved.get("authoringValues").get("maxDiscountAmountMinor").asInt());
        assertEquals("15000", saved.get("config").get("maxDiscountAmount").get("amountMinor").asText());

        // ---- schema 2: VOUCHER
        JsonNode s2 = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "Voucher 30K", "schemaType", "VOUCHER",
                "costSources", List.of("ECOMMERCE"))).header("Authorization", admin)).andExpect(status().isCreated()));
        String sid2 = s2.get("id").asText();
        assertEquals("CART", s2.get("scope").asText());
        assertEquals(2, ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + sid2 + "/condition-config-options").header("Authorization", admin))).size());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid2 + "/condition"), Map.of("configType", "TARGET_SUBTOTAL", "configVersion", "1.0.0",
                "values", Map.of("operator", "GREATER_THAN_OR_EQUAL", "amountMinor", 150000))).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid2 + "/benefit"), Map.of("configType", "VOUCHER", "configVersion", "1.0.0",
                "values", Map.of("discountKind", "FIXED_AMOUNT", "discountValue", 30000))).header("Authorization", admin)).andExpect(status().isOk());

        // authoring mã voucher: còn trống -> canAdd
        JsonNode auth = ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + sid2 + "/voucher-code-authoring").header("Authorization", admin)));
        assertEquals("COLLECTION", auth.get("editorType").asText());
        assertTrue(auth.get("capabilities").get("canAdd").asBoolean());
        assertEquals(0, auth.get("items").size());
        String code = "KM" + t.toUpperCase();
        Map<String, Object> priv = new HashMap<>(Map.of("code", code, "status", "ACTIVE", "visibilityDistributionPolicy",
                Map.of("type", "VOUCHER_VISIBILITY_DISTRIBUTION_POLICY", "version", "1.0.0", "visibilityType", "PRIVATE", "distributionChannels", List.of())));
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid2 + "/voucher-codes"), priv).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid1 + "/voucher-codes"), Map.of("code", code)).header("Authorization", admin))
                .andExpect(status().isBadRequest()); // schema không phải VOUCHER
        JsonNode vc = body(mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + sid2 + "/voucher-codes"), Map.of("code", code.toLowerCase(),
                "status", "ACTIVE", "visibilityDistributionPolicy", Map.of("type", "VOUCHER_VISIBILITY_DISTRIBUTION_POLICY", "version", "1.0.0",
                        "visibilityType", "PUBLIC", "distributionChannels", List.of()))).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(code, vc.get("code").asText());
        assertEquals("ACTIVE", vc.get("status").asText());
        JsonNode auth2 = ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + sid2 + "/voucher-code-authoring").header("Authorization", admin)));
        assertEquals(1, auth2.get("items").size());
        assertFalse(auth2.get("capabilities").get("canAdd").asBoolean());

        // ---- thành phần cấp chiến dịch
        String comp = DRAFTS + "/" + d;
        mvc.perform(jsonRequest(put(comp + "/target"), Map.of("config", Map.of("type", "UPLOAD_FILE", "version", "1.0.0", "uploadBatchId", "x")))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(comp + "/target"), Map.of("config", Map.of("type", "MANUAL", "version", "1.0.0", "skuIds", List.of("99999999"))))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        // trước khi đủ thành phần: chưa sẵn sàng, phát hành bị chặn
        assertEquals("EDITING", ok(mvc.perform(get(comp).header("Authorization", admin))).get("status").asText());
        assertEquals("PROMOTION_DRAFT_NOT_READY", errCode(mvc.perform(post(comp + "/publish").header("Authorization", admin)).andExpect(status().isBadRequest())));
        JsonNode tg = body(mvc.perform(jsonRequest(put(comp + "/target"), Map.of("config", Map.of("type", "MANUAL", "version", "1.0.0",
                "skuIds", List.of(String.valueOf(a[1])), "lineFilter", Map.of("discountState", "ANY")))).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("TARGET", tg.get("componentKind").asText());
        assertEquals(1, tg.get("skus").size());
        mvc.perform(jsonRequest(put(comp + "/audience"), Map.of("config", Map.of("type", "CUSTOMER_SEGMENT", "version", "1.0.0", "segmentIds", List.of("1"))))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(comp + "/audience"), Map.of("config", Map.of("type", "ALL", "version", "1.0.0"))).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(put(comp + "/applicability"), Map.of("config", Map.of("type", "CHANNEL_STORE_APPLICABILITY", "version", "1.0.0",
                "channels", List.of("POS"), "stores", Map.of("type", "ALL", "version", "1.0.0")))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(comp + "/applicability"), Map.of("config", Map.of("type", "CHANNEL_STORE_APPLICABILITY", "version", "1.0.0",
                "channels", List.of("ONLINE"), "stores", Map.of("type", "ALL", "version", "1.0.0")))).header("Authorization", admin)).andExpect(status().isOk());
        JsonNode comps = ok(mvc.perform(get(comp + "/components").header("Authorization", admin)));
        assertEquals(3, comps.get("items").size());
        assertNotNull(comps.get("applicabilityDefault"));

        // ---- danh sách + trạng thái sẵn sàng
        assertEquals("READY_TO_PUBLISH", ok(mvc.perform(get(comp).header("Authorization", admin))).get("status").asText());
        JsonNode list = ok(mvc.perform(get(DRAFTS).param("search", "Chiến dịch " + t).header("Authorization", admin)));
        assertEquals(1, list.get("meta").get("total").asInt());
        assertTrue(list.get("statusCounts").has("DISCARDED"));
        assertEquals(1, list.get("statusCounts").get("READY_TO_PUBLISH").asInt() >= 1 ? 1 : 0);

        // ---- mô phỏng trước khi phát hành (A x2 @100k)
        Map<String, Object> simReq = new HashMap<>();
        simReq.put("scopes", List.of("SKU_ITEM", "CART"));
        simReq.put("evaluatedAt", now.toString());
        simReq.put("customer", Map.of());
        simReq.put("cart", Map.of("currency", "VND"));
        simReq.put("lines", List.of(line("l1", a[1], 2, 100_000), line("l2", b[1], 1, 50_000)));
        simReq.put("couponCodes", List.of(code));
        JsonNode sim = ok(mvc.perform(jsonRequest(post(comp + "/simulate"), simReq).header("Authorization", admin)));
        assertEquals(false, sim.get("simulation").get("persisted").asBoolean());
        // 20% của 100k = 20k bị chặn trên 15k => 30k cho 2 đơn vị; voucher 30k trên (250k-30k)=220k >= 150k điều kiện (chỉ dòng A: 170k)
        assertEquals("60000", sim.get("selection").get("totalSelectedDiscountAmountMinor").asText());
        assertEquals("60000", sim.get("simulation").get("discountDeltaMinor").asText());
        assertEquals("0", sim.get("simulation").get("baselineTotalSelectedDiscountAmountMinor").asText());
        assertEquals(2, sim.get("selection").get("selectedAdjustmentCount").asInt());
        assertTrue(sim.get("simulation").get("conflicts").toString().contains("DRAFT_ADDS_DISCOUNT"));
        JsonNode simVouchers = ok(mvc.perform(jsonRequest(post(comp + "/simulation-vouchers/discover"), Map.of("evaluatedAt", now.toString())).header("Authorization", admin)));
        assertEquals("DRAFT", simVouchers.get("sections").get(0).get("groupType").asText());
        assertEquals(code, simVouchers.get("sections").get(0).get("items").get(0).get("code").asText());

        // ---- phát hành
        JsonNode pub = body(mvc.perform(post(comp + "/publish").header("Authorization", admin)).andExpect(status().isOk()));
        String pid = pub.get("publishedPromotion").get("id").asText();
        assertEquals("ACTIVE", pub.get("publishedPromotion").get("status").asText());
        assertEquals(2, pub.get("componentCounts").get("schemas").asInt());
        assertEquals(64, pub.get("snapshotPayloadHash").asText().length());
        assertEquals("PUBLISHED", ok(mvc.perform(get(comp).header("Authorization", admin))).get("status").asText());
        assertEquals("PROMOTION_DRAFT_NOT_EDITABLE", errCode(mvc.perform(jsonRequest(patch(comp), Map.of("name", "Đổi tên")).header("Authorization", admin))
                .andExpect(status().isConflict())));
        mvc.perform(post(comp + "/publish").header("Authorization", admin)).andExpect(status().isConflict());

        // ---- engine giá thật dùng chiến dịch vừa phát hành (giảm 20% tối đa 15k trên sản phẩm A)
        assertEquals(0, new BigDecimal("15000").compareTo(pricing.unitDiscount(a[0], a[1], new BigDecimal("100000"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(pricing.unitDiscount(b[0], b[1], new BigDecimal("50000"))));

        // ---- voucher validate / discover
        String validate = V1 + UrlAdminConstant.Promotion.ORDER_VOUCHER_VALIDATE;
        JsonNode okV = ok(mvc.perform(jsonRequest(post(validate), Map.of("code", code.toLowerCase(), "checkoutContext",
                Map.of("lines", List.of(line("l1", a[1], 2, 100_000))))).header("Authorization", admin)));
        assertTrue(okV.get("valid").asBoolean());
        assertEquals(code, okV.get("card").get("code").asText());
        JsonNode low = ok(mvc.perform(jsonRequest(post(validate), Map.of("code", code, "checkoutContext",
                Map.of("lines", List.of(line("l1", a[1], 1, 100_000))))).header("Authorization", admin)));
        assertFalse(low.get("valid").asBoolean());
        assertEquals("VOUCHER_MIN_ORDER_NOT_MET", low.get("reasonCode").asText());
        JsonNode unknown = ok(mvc.perform(jsonRequest(post(validate), Map.of("code", "KHONGCO" + t)).header("Authorization", admin)));
        assertEquals("VOUCHER_NOT_FOUND", unknown.get("reasonCode").asText());
        JsonNode disc = ok(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Promotion.ORDER_VOUCHER_DISCOVER), Map.of("items",
                List.of(Map.of("variationId", String.valueOf(a[1]), "quantity", 2, "unitPriceMinor", "100000")))).header("Authorization", admin)));
        assertTrue(disc.toString().contains(code));

        // ---- runtime evaluate (chỉ bản đã phát hành)
        simReq.put("couponCodes", List.of(code));
        JsonNode ev = ok(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Promotion.RUNTIME_EVALUATE), simReq).header("Authorization", admin)));
        assertEquals("60000", ev.get("selection").get("totalSelectedDiscountAmountMinor").asText());

        // ---- tổng quan
        JsonNode ov = ok(mvc.perform(get(CAMPAIGNS).param("search", "Chiến dịch " + t).header("Authorization", admin)));
        assertEquals(1, ov.get("items").size());
        JsonNode item = ov.get("items").get(0);
        assertEquals("PUBLISHED", item.get("source").asText());
        assertEquals("ACTIVE", item.get("promotionStatus").asText());
        assertTrue(item.get("availableActions").toString().contains("PAUSE"));
        assertEquals("Giảm 20%, tối đa 15.000 đ", item.get("schemas").get(0).get("benefitSummary").asText());

        // ---- đọc bản đã phát hành
        assertEquals("ACTIVE", ok(mvc.perform(get(PUBLISHED + "/" + pid).header("Authorization", admin))).get("status").asText());
        assertEquals(0, ok(mvc.perform(get(PUBLISHED + "/" + pid + "/quota-statistics").header("Authorization", admin))).get("quotaStatistics").size());
        JsonNode pSchemas = ok(mvc.perform(get(PUBLISHED + "/" + pid + "/schemas").header("Authorization", admin)));
        assertEquals("RUNNING", pSchemas.get("items").get(0).get("displayStatus").asText());
        assertEquals(code, ok(mvc.perform(get(PUBLISHED + "/" + pid + "/schemas/" + sid2 + "/voucher-codes").header("Authorization", admin)))
                .get("items").get(0).get("code").asText());
        assertEquals("BENEFIT", ok(mvc.perform(get(PUBLISHED + "/" + pid + "/schemas/" + sid1 + "/components").header("Authorization", admin)))
                .get("benefit").get("componentKind").asText());

        // ---- vòng đời: pause -> engine ngừng; resume -> chạy lại; gỡ schema; end
        assertEquals("PAUSED", ok(mvc.perform(post(PUBLISHED + "/" + pid + "/pause").header("Authorization", admin))).get("status").asText());
        assertEquals(0, BigDecimal.ZERO.compareTo(pricing.unitDiscount(a[0], a[1], new BigDecimal("100000"))));
        assertFalse(ok(mvc.perform(jsonRequest(post(validate), Map.of("code", code)).header("Authorization", admin))).get("valid").asBoolean());
        mvc.perform(post(PUBLISHED + "/" + pid + "/pause").header("Authorization", admin)).andExpect(status().isConflict());
        assertEquals("ACTIVE", ok(mvc.perform(post(PUBLISHED + "/" + pid + "/resume").header("Authorization", admin))).get("status").asText());
        assertEquals(0, new BigDecimal("15000").compareTo(pricing.unitDiscount(a[0], a[1], new BigDecimal("100000"))));
        JsonNode un = ok(mvc.perform(post(PUBLISHED + "/" + pid + "/schemas/" + sid1 + "/unpublish").header("Authorization", admin)));
        assertEquals("UNPUBLISHED", un.get("displayStatus").asText());
        assertEquals(0, BigDecimal.ZERO.compareTo(pricing.unitDiscount(a[0], a[1], new BigDecimal("100000"))));
        mvc.perform(post(PUBLISHED + "/" + pid + "/schemas/" + sid1 + "/unpublish").header("Authorization", admin)).andExpect(status().isConflict());
        assertEquals("ENDED", ok(mvc.perform(post(PUBLISHED + "/" + pid + "/end").header("Authorization", admin))).get("status").asText());
        assertFalse(ok(mvc.perform(jsonRequest(post(validate), Map.of("code", code)).header("Authorization", admin))).get("valid").asBoolean());
        mvc.perform(post(PUBLISHED + "/" + pid + "/resume").header("Authorization", admin)).andExpect(status().isConflict());
    }

    @Test
    void discardDraftReorderSchemasAndCustomerLookup() throws Exception {
        admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        Instant now = Instant.now();
        String d = body(mvc.perform(jsonRequest(post(DRAFTS), Map.of("name", "Nháp " + t, "startsAt", now.toString(),
                "endsAt", now.plus(3, ChronoUnit.DAYS).toString())).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        String s1 = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "A", "schemaType", "FLAT_PRICE", "costSources", List.of("RETAIL")))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        String s2 = body(mvc.perform(jsonRequest(post(DRAFTS + "/" + d + "/schemas"), Map.of("name", "B", "schemaType", "MARKDOWN", "costSources", List.of("RETAIL")))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asText();
        // thời gian schema ngoài chiến dịch -> 400
        mvc.perform(jsonRequest(patch(DRAFTS + "/" + d + "/schemas/" + s1), Map.of("schemaEndsAt", now.plus(30, ChronoUnit.DAYS).toString()))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/order"), Map.of("schemaIds", List.of(s2))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/order"), Map.of("schemaIds", List.of(s2, s1))).header("Authorization", admin)).andExpect(status().isOk());
        JsonNode items = ok(mvc.perform(get(DRAFTS + "/" + d + "/schemas").header("Authorization", admin))).get("items");
        assertEquals(s2, items.get(0).get("id").asText());
        assertEquals(0, items.get(0).get("sortOrder").asInt());
        // đồng giá không nhận số lẻ
        mvc.perform(jsonRequest(put(DRAFTS + "/" + d + "/schemas/" + s1 + "/benefit"), Map.of("configType", "FLAT_PRICE", "configVersion", "1.0.0",
                "values", Map.of("fixedPriceAmountMinor", 1000.5))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(delete(DRAFTS + "/" + d + "/schemas/" + s1).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get(DRAFTS + "/" + d + "/schemas/" + s1).header("Authorization", admin)).andExpect(status().isNotFound());
        // huỷ nháp: biến mất khỏi danh sách mặc định, vẫn đếm trong DISCARDED
        mvc.perform(delete(DRAFTS + "/" + d).header("Authorization", admin)).andExpect(status().isOk());
        assertEquals(0, ok(mvc.perform(get(DRAFTS).param("search", "Nháp " + t).header("Authorization", admin))).get("items").size());
        assertEquals(1, ok(mvc.perform(get(DRAFTS).param("search", "Nháp " + t).param("status", "DISCARDED").header("Authorization", admin))).get("items").size());
        mvc.perform(get(DRAFTS + "/999999999").header("Authorization", admin)).andExpect(status().isNotFound());

        // tra khách theo SĐT
        String lookup = V1 + UrlAdminConstant.Promotion.CUSTOMER_BY_PHONE;
        String phone = uniquePhone();
        mvc.perform(get(lookup).param("phone", phone).header("Authorization", admin)).andExpect(status().isNotFound());
        registerCustomer(phone);
        JsonNode c = ok(mvc.perform(get(lookup).param("phone", phone).header("Authorization", admin)));
        assertEquals(phone, c.get("phone").asText());
        assertEquals("ACTIVE", c.get("status").asText());
    }
}

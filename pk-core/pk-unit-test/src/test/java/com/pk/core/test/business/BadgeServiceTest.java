package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.BadgeFlowRepo;
import com.pk.core.business.repository.BadgeTemplateRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.PromotionRepo;
import com.pk.core.business.service.PreOrderScope;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.business.service.impl.BadgeServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.model.dto.request.AdminBadgeFlowRequestDto;
import com.pk.core.model.dto.request.AdminBadgeTemplateRequestDto;
import com.pk.core.model.dto.request.BadgeFlowTemplateRefDto;
import com.pk.core.model.dto.response.BadgeTemplateResponseDto;
import com.pk.core.model.dto.response.SkuBadgeResponseDto;
import com.pk.core.model.entity.BadgeFlowEntity;
import com.pk.core.model.entity.BadgeTemplateEntity;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.PromotionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quy tắc nhãn storefront: PRE_ORDER -> OUT_OF_STOCK -> CAMPAIGN, mỗi SKU tối đa 1 nhãn. */
@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock BadgeTemplateRepo templates;
    @Mock BadgeFlowRepo flows;
    @Mock ProductSkuRepo productSkus;
    @Mock ProductRepo products;
    @Mock CollectionRepo collections;
    @Mock PromotionRepo promotions;
    @Mock CategoryRepo categories;
    @Mock PreOrderService preOrder;

    private BadgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BadgeServiceImpl(templates, flows, productSkus, products, collections, promotions, categories, preOrder,
                new ObjectMapper());
    }

    private static BadgeTemplateEntity template(long id, String code, String badgeType, int weight) {
        BadgeTemplateEntity t = new BadgeTemplateEntity();
        t.setId(id);
        t.setCode(code);
        t.setName(code);
        t.setType("TEXT");
        t.setBadgeType(badgeType);
        t.setStatus(BadgeTemplateEntity.ACTIVE);
        t.setDefaultPriorityWeight(weight);
        return t;
    }

    private static BadgeFlowEntity flow(long id, String ruleType, String ruleConfig, String refsJson) {
        BadgeFlowEntity f = new BadgeFlowEntity();
        f.setId(id);
        f.setName("flow-" + id);
        f.setStatus(BadgeFlowEntity.ACTIVE);
        f.setRuleType(ruleType);
        f.setRuleConfig(ruleConfig);
        f.setChannel("ALL");
        f.setTemplates(refsJson);
        return f;
    }

    private static String ref(long templateId, int weight, boolean pinned) {
        return "{\"badgeTemplateId\":\"" + templateId + "\",\"priorityWeight\":" + weight + ",\"isPinned\":" + pinned + "}";
    }

    private static ProductSkuEntity sku(long id, long productId, int onHand) {
        ProductSkuEntity s = new ProductSkuEntity("SKU-" + id, "M", BigDecimal.valueOf(1_000_000), onHand);
        s.setId(id);
        s.setProductId(productId);
        s.setStatus(ProductSkuEntity.PUBLISHED);
        return s;
    }

    private void stub(List<BadgeTemplateEntity> t, List<BadgeFlowEntity> f, boolean preOrderEnabled) {
        when(templates.findAll(any(Sort.class))).thenReturn(t);
        when(flows.findAll(any(Sort.class))).thenReturn(f);
        when(preOrder.scope()).thenReturn(PreOrderScope.all(preOrderEnabled));
    }

    private String onlyBadgeCode(List<SkuBadgeResponseDto> res) {
        assertEquals(1, res.size());
        assertTrue(res.get(0).getBadges().size() <= 1, "mỗi SKU tối đa 1 nhãn");
        return res.get(0).getBadges().isEmpty() ? null : res.get(0).getBadges().get(0).getCode();
    }

    @Test
    void outOfStockWithPreOrderEnabledGetsPreOrderBadge() {
        stub(List.of(template(1, "PRE", "PRE_ORDER", 5), template(2, "OOS", "OUT_OF_STOCK", 5)),
                List.of(flow(10, "OUT_OF_STOCK", null, "[" + ref(1, 3, false) + "," + ref(2, 9, false) + "]")), true);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 0));

        assertEquals("PRE", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void outOfStockWithPreOrderDisabledGetsOutOfStockBadge() {
        stub(List.of(template(1, "PRE", "PRE_ORDER", 5), template(2, "OOS", "OUT_OF_STOCK", 5)),
                List.of(flow(10, "OUT_OF_STOCK", null, "[" + ref(1, 3, false) + "," + ref(2, 9, false) + "]")), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 0));

        assertEquals("OOS", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void outOfStockFallsBackToActiveTemplateWhenNoFlowConfigured() {
        stub(List.of(template(2, "OOS", "OUT_OF_STOCK", 5)), List.of(), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 0));

        assertEquals("OOS", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void inStockSkuGetsCampaignBadgeFromAllFlow() {
        stub(List.of(template(3, "SALE", "CAMPAIGN", 5)), List.of(flow(11, "ALL", null, "[" + ref(3, 1, false) + "]")), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));

        assertEquals("SALE", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void pinnedTemplateBeatsHigherWeightAndOnlyOneBadgeIsReturned() {
        stub(List.of(template(3, "SALE", "CAMPAIGN", 50), template(4, "NEW", "NEW_ARRIVAL", 1)),
                List.of(flow(11, "ALL", null, "[" + ref(3, 99, false) + "," + ref(4, 1, true) + "]")), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));

        assertEquals("NEW", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void promotionFlowNeedsAnActivePromotionOnTheProduct() {
        stub(List.of(template(3, "SALE", "CAMPAIGN", 5)),
                List.of(flow(12, "PROMOTION", null, "[" + ref(3, 1, false) + "]")), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));

        assertEquals(null, onlyBadgeCode(service.forSkus(List.of("7"), null)));

        PromotionEntity promo = new PromotionEntity();
        promo.setId(1L);
        when(promotions.findActiveByProductId(any(), any(Date.class))).thenReturn(List.of(promo));
        assertEquals("SALE", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void categoryFlowMatchesOnlyThatCategory() {
        stub(List.of(template(4, "NEW", "NEW_ARRIVAL", 1)),
                List.of(flow(13, "CATEGORY", "{\"categoryIds\":[\"5\"]}", "[" + ref(4, 1, false) + "]")), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));
        ProductEntity product = new ProductEntity(5L, "P", "SP", "sp");
        product.setId(100L);
        when(products.findOne(100L)).thenReturn(product);

        assertEquals("NEW", onlyBadgeCode(service.forSkus(List.of("7"), null)));

        product.setCategoryId(6L);
        assertEquals(null, onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void flowOutsideItsTimeWindowIsIgnored() {
        BadgeFlowEntity expired = flow(14, "ALL", null, "[" + ref(3, 1, false) + "]");
        expired.setActiveTo(new Date(System.currentTimeMillis() - 3_600_000));
        stub(List.of(template(3, "SALE", "CAMPAIGN", 5)), List.of(expired), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));

        assertEquals(null, onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void createFlowRejectsUnknownTemplate() {
        when(templates.findOne(99L)).thenReturn(null);
        AdminBadgeFlowRequestDto req = new AdminBadgeFlowRequestDto("Flow", null, null, null, "ACTIVE", "ALL", null, "ALL",
                List.of(new BadgeFlowTemplateRefDto("99", 1, false)));

        assertThrows(BusinessException.class, () -> service.createFlow(req));
    }

    private void stubScope(PreOrderScope scope, List<BadgeTemplateEntity> t, List<BadgeFlowEntity> f) {
        when(templates.findAll(any(Sort.class))).thenReturn(t);
        when(flows.findAll(any(Sort.class))).thenReturn(f);
        when(preOrder.scope()).thenReturn(scope);
    }

    private static final List<BadgeTemplateEntity> ALL_TYPES = List.of(template(1, "PRE", "PRE_ORDER", 5),
            template(2, "OOS", "OUT_OF_STOCK", 5), template(3, "SALE", "CAMPAIGN", 5));

    @Test
    void scopedPreOrderSkuShowsPreOrderEvenWhenInStock() {
        stubScope(new PreOrderScope(true, PreOrderScope.SKU, java.util.Set.of(7L)), ALL_TYPES,
                List.of(flow(11, "ALL", null, "[" + ref(3, 1, false) + "]")));
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5)); // còn hàng nhưng nằm trong cấu hình đặt trước
        when(products.findOne(100L)).thenReturn(null);

        assertEquals("PRE", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void outOfStockSkuOutsideScopedPreOrderGetsOutOfStockNotPreOrder() {
        stubScope(new PreOrderScope(true, PreOrderScope.SKU, java.util.Set.of(7L)), ALL_TYPES, List.of());
        when(productSkus.findOne(8L)).thenReturn(sku(8, 100, 0));
        when(products.findOne(100L)).thenReturn(null);

        assertEquals("OOS", onlyBadgeCode(service.forSkus(List.of("8"), null)));
    }

    @Test
    void preOrderBeatsOutOfStockBeatsCampaign() {
        BadgeFlowEntity campaign = flow(11, "ALL", null, "[" + ref(3, 99, true) + "]");
        // 1) hết hàng + trong phạm vi -> PRE (dù campaign được ghim)
        stubScope(new PreOrderScope(true, PreOrderScope.PRODUCT, java.util.Set.of(100L)), ALL_TYPES, List.of(campaign));
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 0));
        when(products.findOne(100L)).thenReturn(null);
        assertEquals("PRE", onlyBadgeCode(service.forSkus(List.of("7"), null)));
        // 2) hết hàng + ngoài phạm vi -> OOS (campaign không hiện cho SKU hết hàng)
        when(productSkus.findOne(8L)).thenReturn(sku(8, 200, 0));
        when(products.findOne(200L)).thenReturn(null);
        assertEquals("OOS", onlyBadgeCode(service.forSkus(List.of("8"), null)));
        // 3) còn hàng + ngoài phạm vi -> CAMPAIGN
        when(productSkus.findOne(9L)).thenReturn(sku(9, 200, 3));
        assertEquals("SALE", onlyBadgeCode(service.forSkus(List.of("9"), null)));
    }

    @Test
    void scopedPreOrderWithoutPreOrderTemplateFallsBackToCampaignWhenInStock() {
        stubScope(new PreOrderScope(true, PreOrderScope.SKU, java.util.Set.of(7L)),
                List.of(template(3, "SALE", "CAMPAIGN", 5)), List.of(flow(11, "ALL", null, "[" + ref(3, 1, false) + "]")));
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));
        when(products.findOne(100L)).thenReturn(null);

        assertEquals("SALE", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void disabledPreOrderNeverLabelsPreOrder() {
        stubScope(PreOrderScope.off(), ALL_TYPES, List.of());
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 0));

        assertEquals("OOS", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    // ===================== v5: scope payload, validate, guard =====================

    private AdminBadgeFlowRequestDto flowReq(String status, String ruleType, Map<String, Object> rule) {
        return new AdminBadgeFlowRequestDto("Flow", null, null, null, status, ruleType, rule, "ALL",
                List.of(new BadgeFlowTemplateRefDto("3", 1, false)));
    }

    private JsonNode savedRule() throws Exception {
        ArgumentCaptor<BadgeFlowEntity> captor = ArgumentCaptor.forClass(BadgeFlowEntity.class);
        verify(flows).create(captor.capture());
        return new ObjectMapper().readTree(captor.getValue().getRuleConfig());
    }

    @Test
    void categoryPickerPayloadIsNormalizedToCategoryIdsAndKeptForReopen() throws Exception {
        when(templates.findOne(3L)).thenReturn(template(3, "SALE", "CAMPAIGN", 5));
        CategoryEntity cat = new CategoryEntity("Nhẫn", "nhan", null);
        cat.setId(1L);
        when(categories.findOne(1L)).thenReturn(cat);

        service.createFlow(flowReq("ACTIVE", "CATEGORY",
                Map.of("categories", List.of(Map.of("categoryId", 1, "name", "Nhẫn")))));

        JsonNode rule = savedRule();
        assertEquals(1L, rule.get("categoryIds").get(0).asLong());
        assertTrue(rule.has("categories"), "giữ payload gốc để FE mở lại picker");
    }

    @Test
    void manualTargetWithVariantOnlyAppliesThatSkuNotWholeProduct() throws Exception {
        when(templates.findOne(3L)).thenReturn(template(3, "SALE", "CAMPAIGN", 5));
        when(productSkus.findOne(11L)).thenReturn(sku(11, 10, 1));

        service.createFlow(flowReq("ACTIVE", "MANUAL", Map.of("targets",
                List.of(Map.of("productId", 10, "productVariantId", 11, "skuCode", "R01")))));

        JsonNode rule = savedRule();
        assertEquals(11L, rule.get("skuIds").get(0).asLong());
        assertFalse(rule.has("productIds"));
    }

    @Test
    void promotionPickerPayloadIsNormalizedToPromotionIds() throws Exception {
        when(templates.findOne(3L)).thenReturn(template(3, "SALE", "CAMPAIGN", 5));
        PromotionEntity promo = new PromotionEntity();
        promo.setId(5L);
        when(promotions.findOne(5L)).thenReturn(promo);

        service.createFlow(flowReq("ACTIVE", "PROMOTION",
                Map.of("promotions", List.of(Map.of("id", 5, "name", "Sale")))));

        assertEquals(5L, savedRule().get("promotionIds").get(0).asLong());
    }

    @Test
    void activeFlowWithEmptyScopeIsRejected() {
        when(templates.findOne(3L)).thenReturn(template(3, "SALE", "CAMPAIGN", 5));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createFlow(flowReq("ACTIVE", "CATEGORY", null)));
        assertEquals("BADGE_SCOPE_INVALID", ex.getCode());
        verify(flows, never()).create(any());
    }

    @Test
    void activeFlowWithUnknownCategoryIsRejected() {
        when(templates.findOne(3L)).thenReturn(template(3, "SALE", "CAMPAIGN", 5));

        assertThrows(BusinessException.class, () -> service.createFlow(
                flowReq("ACTIVE", "CATEGORY", Map.of("categoryIds", List.of(99)))));
    }

    @Test
    void draftFlowMayHaveEmptyScope() {
        when(templates.findOne(3L)).thenReturn(template(3, "SALE", "CAMPAIGN", 5));

        service.createFlow(flowReq("DRAFT", "CATEGORY", null));

        verify(flows).create(any(BadgeFlowEntity.class));
    }

    @Test
    void legacyFlowSavedWithPickerPayloadStillMatchesAtRuntime() {
        stub(List.of(template(4, "NEW", "NEW_ARRIVAL", 1)),
                List.of(flow(13, "CATEGORY", "{\"categories\":[{\"categoryId\":5,\"name\":\"Nhẫn\"}]}",
                        "[" + ref(4, 1, false) + "]")), false);
        when(productSkus.findOne(7L)).thenReturn(sku(7, 100, 5));
        ProductEntity product = new ProductEntity(5L, "P", "SP", "sp");
        product.setId(100L);
        when(products.findOne(100L)).thenReturn(product);

        assertEquals("NEW", onlyBadgeCode(service.forSkus(List.of("7"), null)));
    }

    @Test
    void runningOrScheduledFlowCannotBeDeletedButPausedCan() {
        BadgeFlowEntity running = flow(20, "ALL", null, "[]");
        when(flows.findOne(20L)).thenReturn(running);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteFlow("20"));
        assertEquals("BADGE_FLOW_LOCKED", ex.getCode());

        BadgeFlowEntity scheduled = flow(21, "ALL", null, "[]");
        scheduled.setActiveFrom(new Date(System.currentTimeMillis() + 3_600_000));
        when(flows.findOne(21L)).thenReturn(scheduled);
        assertThrows(BusinessException.class, () -> service.deleteFlow("21"));

        BadgeFlowEntity paused = flow(22, "ALL", null, "[]");
        paused.setStatus(BadgeFlowEntity.INACTIVE);
        when(flows.findOne(22L)).thenReturn(paused);
        service.deleteFlow("22");
        verify(flows).delete(paused);
    }

    @Test
    void endedFlowCannotBeEdited() {
        BadgeFlowEntity ended = flow(30, "ALL", null, "[]");
        ended.setActiveTo(new Date(System.currentTimeMillis() - 3_600_000));
        when(flows.findOne(30L)).thenReturn(ended);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateFlow("30", flowReq("ACTIVE", "ALL", null)));
        assertEquals("BADGE_FLOW_LOCKED", ex.getCode());
    }

    @Test
    void activeOrReferencedTemplateCannotBeDeleted() {
        BadgeTemplateEntity active = template(3, "SALE", "CAMPAIGN", 5);
        when(templates.findOne(3L)).thenReturn(active);
        assertThrows(BusinessException.class, () -> service.deleteTemplate("3"));

        BadgeTemplateEntity draft = template(4, "NEW", "NEW_ARRIVAL", 1);
        draft.setStatus(BadgeTemplateEntity.DRAFT);
        when(templates.findOne(4L)).thenReturn(draft);
        when(flows.findAll(any(Sort.class))).thenReturn(List.of(flow(1, "ALL", null, "[" + ref(4, 1, false) + "]")));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteTemplate("4"));
        assertEquals("BADGE_IN_USE", ex.getCode());
    }

    @Test
    void unreferencedDraftTemplateIsSoftDeleted() {
        BadgeTemplateEntity draft = template(5, "OLD", "CAMPAIGN", 1);
        draft.setStatus(BadgeTemplateEntity.DRAFT);
        when(templates.findOne(5L)).thenReturn(draft);
        when(flows.findAll(any(Sort.class))).thenReturn(List.of());

        service.deleteTemplate("5");

        assertNotNull(draft.getDeletedDate());
        verify(templates).update(draft);
    }

    @Test
    void templateResponseCarriesMobileAssetsAndAssetMeta() {
        BadgeTemplateEntity t = template(6, "IMG", "CAMPAIGN", 1);
        t.setType("IMAGE");
        t.setImage("d.png");
        t.setImageMobile("m.png");
        t.setIconMobile("mi.png");
        t.setAssetMeta("{\"width\":64}");
        when(templates.findOne(6L)).thenReturn(t);

        BadgeTemplateResponseDto dto = service.findTemplateById("6");

        assertEquals("m.png", dto.getImageMobile());
        assertEquals("mi.png", dto.getIconMobile());
        assertEquals(64, ((Number) dto.getAssetMeta().get("width")).intValue());
    }

    @Test
    void patchClearsAssetWithEmptyStringAndChangingTypeDropsUnusedAssets() {
        BadgeTemplateEntity t = template(7, "IMG", "CAMPAIGN", 1);
        t.setType("IMAGE");
        t.setImage("d.png");
        t.setImageMobile("m.png");
        t.setAssetMeta("{\"width\":64}");
        when(templates.findOne(7L)).thenReturn(t);

        AdminBadgeTemplateRequestDto clear = new AdminBadgeTemplateRequestDto();
        clear.setImage("");
        service.updateTemplate("7", clear);
        assertNull(t.getImage());
        assertEquals("m.png", t.getImageMobile());

        AdminBadgeTemplateRequestDto toText = new AdminBadgeTemplateRequestDto();
        toText.setType("TEXT");
        service.updateTemplate("7", toText);
        assertNull(t.getImageMobile());
        assertNull(t.getAssetMeta());
    }

    // ---------------------------------------------------------------- P1: search / displayStatus / counts

    private static BadgeFlowEntity named(long id, String name, String status, Date from, Date to) {
        BadgeFlowEntity f = flow(id, "ALL", null, "[]");
        f.setName(name);
        f.setStatus(status);
        f.setActiveFrom(from);
        f.setActiveTo(to);
        return f;
    }

    private List<BadgeFlowEntity> campaigns() {
        Date hourAgo = new Date(System.currentTimeMillis() - 3_600_000);
        Date hourLater = new Date(System.currentTimeMillis() + 3_600_000);
        return List.of(
                named(1, "Sale mùa hè", BadgeFlowEntity.ACTIVE, hourAgo, hourLater),          // RUNNING
                named(2, "Sale mùa đông", BadgeFlowEntity.ACTIVE, hourLater, null),           // SCHEDULED
                named(3, "Khai trương", BadgeFlowEntity.ACTIVE, null, hourAgo),               // ENDED
                named(4, "Nháp sale", BadgeFlowEntity.DRAFT, null, null),                     // DRAFT
                named(5, "Tạm dừng", BadgeFlowEntity.INACTIVE, null, null));                  // PAUSED
    }

    @Test
    void flowListFiltersByDisplayStatusAndCountsAreIndependentOfFilter() {
        when(flows.findAll(any(Sort.class))).thenReturn(campaigns());

        var running = service.findFlows(1, 20, null, null, "running");

        assertEquals(1, running.getTotal());
        assertEquals("RUNNING", running.getItems().get(0).getDisplayStatus());
        assertEquals(5, running.getCounts().getAll());
        assertEquals(1L, running.getCounts().getDisplayStatus().get("SCHEDULED"));
        assertEquals(1L, running.getCounts().getDisplayStatus().get("ENDED"));
        assertEquals(1L, running.getCounts().getDisplayStatus().get("PAUSED"));
        assertEquals(1L, running.getCounts().getDisplayStatus().get("DRAFT"));
    }

    @Test
    void flowListSearchMatchesNameAndNarrowsCounts() {
        when(flows.findAll(any(Sort.class))).thenReturn(campaigns());

        var sale = service.findFlows(1, 20, null, "SALE", null);

        assertEquals(3, sale.getTotal());
        assertEquals(3, sale.getCounts().getAll());
        assertEquals(1L, sale.getCounts().getDraft());
    }

    @Test
    void flowListRejectsUnknownDisplayStatus() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.findFlows(1, 20, null, null, "LIVE"));
        assertEquals("VALIDATION_FAILED", ex.getCode());
    }

    @Test
    void templateListSearchMatchesNameOrCode() {
        when(templates.findAll(any(Sort.class))).thenReturn(List.of(template(1, "SALE_TAG", "CAMPAIGN", 1),
                template(2, "NEW_TAG", "CAMPAIGN", 1)));

        var found = service.findTemplates(1, 20, null, null, null, null, "sale");

        assertEquals(1, found.getTotal());
        assertEquals(1, found.getItems().size());
    }
}

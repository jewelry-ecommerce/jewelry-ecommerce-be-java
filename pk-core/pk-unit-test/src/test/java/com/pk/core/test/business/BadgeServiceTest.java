package com.pk.core.test.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.BadgeFlowRepo;
import com.pk.core.business.repository.BadgeTemplateRepo;
import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.repository.PromotionRepo;
import com.pk.core.business.service.PreOrderScope;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.business.service.impl.BadgeServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.model.dto.request.AdminBadgeFlowRequestDto;
import com.pk.core.model.dto.request.BadgeFlowTemplateRefDto;
import com.pk.core.model.dto.response.SkuBadgeResponseDto;
import com.pk.core.model.entity.BadgeFlowEntity;
import com.pk.core.model.entity.BadgeTemplateEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.PromotionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock PreOrderService preOrder;

    private BadgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BadgeServiceImpl(templates, flows, productSkus, products, collections, promotions, preOrder,
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
}

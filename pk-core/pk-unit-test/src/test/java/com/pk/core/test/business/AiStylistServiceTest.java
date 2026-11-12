package com.pk.core.test.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.ai.AiCandidate;
import com.pk.core.business.ai.AiProperties;
import com.pk.core.business.ai.AiRankedPick;
import com.pk.core.business.ai.AiRankingProvider;
import com.pk.core.business.ai.AiRankingResult;
import com.pk.core.business.ai.AiRecommendationOrchestrator;
import com.pk.core.business.ai.RuleBasedAiRankingProvider;
import com.pk.core.business.repository.AiStylistFeedbackRepo;
import com.pk.core.business.repository.AiStylistSessionItemRepo;
import com.pk.core.business.repository.AiStylistSessionRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.impl.AiStylistServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.StylistFeedbackRequestDto;
import com.pk.core.model.dto.request.StylistRequestDto;
import com.pk.core.model.dto.response.SetResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationListResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationResponseDto;
import com.pk.core.model.dto.response.StylistSessionResponseDto;
import com.pk.core.model.entity.AiStylistSessionEntity;
import com.pk.core.model.entity.AiStylistSessionItemEntity;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStylistServiceTest {

    private static final Long USER_ID = 9L;

    @Mock ProductRepo products;
    @Mock ProductSkuRepo productSkus;
    @Mock CategoryRepo categories;
    @Mock AiStylistSessionRepo sessions;
    @Mock AiStylistSessionItemRepo sessionItems;
    @Mock AiStylistFeedbackRepo feedback;
    @Mock AiRankingProvider gemini;

    private final Map<Long, Long> prices = new HashMap<>();
    private AiProperties properties;
    private AiStylistServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        AiRecommendationOrchestrator orchestrator = new AiRecommendationOrchestrator(
                products, productSkus, categories, feedback, properties, gemini,
                new RuleBasedAiRankingProvider(properties));
        service = new AiStylistServiceImpl(orchestrator, sessions, sessionItems, feedback, properties,
                new ObjectMapper());
        lenient().when(categories.findOne(anyLong())).thenAnswer(inv -> category(inv.getArgument(0)));
        lenient().when(feedback.findDislikedProductIds(anyLong())).thenReturn(List.of());
        lenient().when(productSkus.findByProductId(anyLong())).thenAnswer(inv -> {
            long productId = inv.getArgument(0);
            long price = prices.getOrDefault(productId, 0L);
            return List.of(sku(productId, productId, price));
        });
        lenient().when(sessions.create(any(AiStylistSessionEntity.class))).thenAnswer(inv -> {
            AiStylistSessionEntity session = inv.getArgument(0);
            session.setId(100L);
            return session;
        });
        lenient().when(sessionItems.create(any(AiStylistSessionItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void guestRecommendationUsesFallbackBannerAndSkipsHistory() {
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(2L, 2L, "cưới", 1_050_000L));

        StylistRecommendationListResponseDto res = service.recommend(request("cưới", null, "ITEM"), null);

        assertEquals(AiRankingResult.SOURCE_FALLBACK, res.getSource());
        assertEquals(AiRankingResult.FALLBACK_BANNER, res.getBanner());
        assertNull(res.getSessionId());
        assertEquals(2, res.getRecommendations().size());
        StylistRecommendationResponseDto first = res.getRecommendations().get(0);
        assertEquals("SKU-1", first.getSkuCode());
        assertEquals(0, BigDecimal.valueOf(1_000_000L).compareTo(first.getPrice()));
        assertEquals(8, first.getStock());
        verify(sessions, never()).create(any());
        verify(gemini, never()).rank(any(), any(), anyBoolean());
    }

    @Test
    void signedInRecommendationPersistsAndTrimsToTwenty() {
        catalog(published(1L, 1L, "cưới", 1_000_000L));
        StylistRequestDto req = request("cưới", new BigDecimal("1000000"), "item");
        req.setStyle(List.of("vàng"));
        req.setRecipient("mẹ");

        StylistRecommendationListResponseDto res = service.recommend(req, USER_ID);

        assertEquals(100L, res.getSessionId());
        ArgumentCaptor<AiStylistSessionEntity> saved = ArgumentCaptor.forClass(AiStylistSessionEntity.class);
        verify(sessions).create(saved.capture());
        assertEquals(USER_ID, saved.getValue().getUserId());
        assertEquals("ITEM", saved.getValue().getBudgetScope());
        assertEquals(AiRankingResult.SOURCE_FALLBACK, saved.getValue().getSource());
        assertTrue(saved.getValue().getStylesJson().contains("vàng"));
        verify(sessionItems, times(1)).create(any(AiStylistSessionItemEntity.class));
        verify(sessions).trimTo(USER_ID, 20);
    }

    @Test
    void itemBudgetKeepsPricesInsideTolerance() {
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(2L, 2L, "cưới", 500_000L),
                published(3L, 3L, "cưới", 1_050_000L));

        StylistRecommendationListResponseDto res = service.recommend(
                request("cưới", new BigDecimal("1000000"), "ITEM"), null);

        List<Long> ids = res.getRecommendations().stream().map(StylistRecommendationResponseDto::getId).toList();
        assertEquals(List.of(1L, 3L), ids.stream().sorted().toList());
    }

    @Test
    void providerFailureFallsBackToBasicBanner() {
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(2L, 2L, "cưới", 1_100_000L),
                published(3L, 3L, "cưới", 900_000L));
        when(gemini.rank(any(), any(), anyBoolean())).thenThrow(new RuntimeException("down"));

        StylistRecommendationListResponseDto res = service.recommend(request("cưới", null, null), null);

        assertEquals(AiRankingResult.SOURCE_FALLBACK, res.getSource());
        assertEquals("Gợi ý cơ bản", res.getBanner());
        assertFalse(res.getRecommendations().isEmpty());
    }

    @Test
    void readyProviderResultIsReturnedWithoutBanner() {
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(2L, 2L, "cưới", 1_000_000L),
                published(3L, 3L, "cưới", 1_000_000L));
        when(gemini.rank(any(), any(), anyBoolean())).thenAnswer(inv -> {
            List<AiCandidate> candidates = inv.getArgument(1);
            List<AiRankedPick> picks = new ArrayList<>();
            for (AiCandidate candidate : candidates) {
                picks.add(new AiRankedPick(candidate.productId(), "Model chọn", 0.91));
            }
            return new AiRankingResult(AiRankingResult.SOURCE_AI, null, null, picks);
        });

        StylistRecommendationListResponseDto res = service.recommend(request("cưới", null, "ITEM"), null);

        assertEquals(AiRankingResult.SOURCE_AI, res.getSource());
        assertNull(res.getBanner());
        assertEquals(3, res.getRecommendations().size());
        assertEquals("Model chọn", res.getRecommendations().get(0).getReason());
    }

    @Test
    void dislikedProductsAreExcluded() {
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(3L, 3L, "cưới", 1_000_000L));
        when(feedback.findDislikedProductIds(USER_ID)).thenReturn(List.of(1L));

        StylistRecommendationListResponseDto res = service.recommend(
                request("cưới", new BigDecimal("1000000"), "ITEM"), USER_ID);

        List<Long> ids = res.getRecommendations().stream().map(StylistRecommendationResponseDto::getId).toList();
        assertEquals(List.of(3L), ids);
    }

    @Test
    void setBudgetKeepsTotalWithinTolerance() {
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(2L, 2L, "cưới", 1_000_000L),
                published(3L, 3L, "cưới", 1_000_000L));

        SetResponseDto res = service.buildSet(request("cưới", new BigDecimal("3000000"), "SET"), null);

        assertEquals(3, res.getItems().size());
        assertEquals(0, new BigDecimal("3000000").compareTo(res.getTotalPrice()));
        assertTrue(res.getItems().get(0).isKeyPiece());
        assertEquals("SKU-1", res.getItems().get(0).getSkuCode());
        assertEquals(8, res.getItems().get(0).getStock());
        assertEquals(AiRankingResult.FALLBACK_BANNER, res.getBanner());
    }

    @Test
    void setOverBudgetIsRejected() {
        catalog(published(1L, 1L, "cưới", 1_000_000L), published(2L, 2L, "cưới", 1_000_000L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.buildSet(request("cưới", new BigDecimal("1500000"), "SET"), null));

        assertEquals(ErrorCode.AI_BUDGET_EXCEEDED, ex.getCode());
        verify(sessions, never()).create(any());
    }

    @Test
    void unknownBudgetScopeIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recommend(request("cưới", null, "PAIR"), null));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        assertEquals("budgetScope", ex.getField());
        verify(products, never()).searchPublished(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void feedbackUpsertsWhenSessionBelongsToUser() {
        AiStylistSessionEntity session = new AiStylistSessionEntity();
        session.setId(5L);
        session.setUserId(USER_ID);
        when(sessions.findOne(5L)).thenReturn(session);
        AiStylistSessionItemEntity item = new AiStylistSessionItemEntity();
        item.setProductId(8L);
        when(sessionItems.findBySessionId(5L)).thenReturn(List.of(item));

        var res = service.feedback(new StylistFeedbackRequestDto(5L, 8L, false), USER_ID);

        assertFalse(res.isLiked());
        verify(feedback).upsert(5L, USER_ID, 8L, false);
    }

    @Test
    void feedbackUnknownSessionThrows() {
        when(sessions.findOne(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.feedback(new StylistFeedbackRequestDto(5L, 8L, true), USER_ID));

        assertEquals(ErrorCode.AI_SESSION_NOT_FOUND, ex.getCode());
        verify(feedback, never()).upsert(anyLong(), anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void historyMapsStoredSession() {
        AiStylistSessionEntity row = new AiStylistSessionEntity();
        row.setId(5L);
        row.setOccasion("cưới");
        row.setStylesJson("[\"cổ điển\"]");
        row.setBudget(new BigDecimal("1000000"));
        row.setBudgetScope("ITEM");
        row.setSource(AiRankingResult.SOURCE_FALLBACK);
        row.setFallbackReason("rule_based");
        when(sessions.recent(USER_ID, 20)).thenReturn(List.of(row));
        AiStylistSessionItemEntity item = new AiStylistSessionItemEntity();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setReason("Phù hợp");
        item.setMatchScore(new BigDecimal("0.7500"));
        item.setSortOrder(0);
        when(sessionItems.findBySessionId(5L)).thenReturn(List.of(item));

        List<StylistSessionResponseDto> history = service.history(USER_ID);

        assertEquals(1, history.size());
        assertEquals(List.of("cổ điển"), history.get(0).getStyles());
        assertEquals(1L, history.get(0).getItems().get(0).getProductId());
        assertEquals(0, new BigDecimal("0.7500").compareTo(history.get(0).getItems().get(0).getMatchScore()));
    }

    private void catalog(ProductEntity... rows) {
        when(products.searchPublished(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(List.of(rows));
    }

    private ProductEntity published(long id, long categoryId, String occasion, long price) {
        prices.put(id, price);
        ProductEntity product = new ProductEntity(categoryId, "P-" + id, "Nhẫn " + id, "nhan-" + id);
        product.setId(id);
        product.setStatus(ProductEntity.PUBLISHED);
        product.setOccasion(occasion);
        product.setMaterial("vàng");
        product.setThumbnailUrl("https://img/" + id);
        return product;
    }

    private static ProductSkuEntity sku(long id, long productId, long price) {
        ProductSkuEntity entity = new ProductSkuEntity("SKU-" + id, "M", BigDecimal.valueOf(price), 8);
        entity.setId(id);
        entity.setProductId(productId);
        entity.setStatus(ProductSkuEntity.PUBLISHED);
        entity.setDefault(true);
        return entity;
    }

    private static CategoryEntity category(long id) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(id);
        entity.setName("Danh mục " + id);
        return entity;
    }

    private static StylistRequestDto request(String occasion, BigDecimal budget, String scope) {
        StylistRequestDto req = new StylistRequestDto();
        req.setOccasion(occasion);
        req.setBudget(budget);
        req.setBudgetScope(scope);
        return req;
    }
}

package com.pk.core.business.service.impl;

import com.pk.core.business.ai.AiCandidate;
import com.pk.core.business.ai.AiRankingResult;
import com.pk.core.business.ai.AssistantContext;
import com.pk.core.business.ai.AssistantReplyParser;
import com.pk.core.business.ai.AssistantTurn;
import com.pk.core.model.dto.request.AssistantHistoryItemDto;
import com.pk.core.business.ai.ShopAssistantService;
import com.pk.core.business.service.AiAssistantService;
import com.pk.core.model.dto.response.AssistantMessageResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Cửa chat. Việc gọi model và tool nằm ở ShopAssistantService. */
@Service
@Slf4j
public class AiAssistantChatService implements AiAssistantService {

    static final String FALLBACK_REPLY = "Xin chào, shop có thể hỗ trợ bạn chọn trang sức ạ. Bạn cần tư vấn món nào?";

    private final ShopAssistantService shop;

    public AiAssistantChatService(ShopAssistantService shop) {
        this.shop = shop;
    }

    @Override
    public AssistantMessageResponseDto chat(String message, List<AssistantHistoryItemDto> history) {
        String text = message == null ? "" : message.trim();
        try {
            AssistantTurn turn = shop.answer(text, AssistantContext.from(history));
            return reply(turn.draft(), turn.products());
        } catch (Exception ex) {
            log.warn("AI_ASSISTANT_PROVIDER_FALLBACK reason={}", ex.getMessage());
            AssistantMessageResponseDto fallback = new AssistantMessageResponseDto(FALLBACK_REPLY, List.of(), null);
            fallback.setProviderStatus(ex.getMessage());
            return fallback;
        }
    }

    private static AssistantMessageResponseDto reply(AssistantReplyParser.Draft draft, List<AiCandidate> catalog) {
        String text = draft.reply() == null || draft.reply().isBlank() ? FALLBACK_REPLY : draft.reply();
        return new AssistantMessageResponseDto(text, cards(catalog, draft.productIds()), null);
    }

    private static List<StylistRecommendationResponseDto> cards(List<AiCandidate> catalog, List<Long> ids) {
        List<StylistRecommendationResponseDto> cards = new ArrayList<>();
        if (ids == null) {
            return cards;
        }
        for (Long id : ids) {
            AiCandidate candidate = find(catalog, id);
            if (candidate != null) {
                cards.add(card(candidate));
            }
        }
        return cards;
    }

    private static AiCandidate find(List<AiCandidate> catalog, Long id) {
        for (AiCandidate candidate : catalog) {
            if (id != null && id == candidate.productId()) {
                return candidate;
            }
        }
        return null;
    }

    private static StylistRecommendationResponseDto card(AiCandidate candidate) {
        return new StylistRecommendationResponseDto("PRODUCT", candidate.productId(), candidate.name(),
                candidate.slug(), candidate.thumbnailUrl(), "", 0.5d, candidate.skuId(), candidate.skuCode(),
                candidate.price(), candidate.stock(), AiRankingResult.SOURCE_AI);
    }
}

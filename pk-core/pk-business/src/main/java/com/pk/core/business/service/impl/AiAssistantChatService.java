package com.pk.core.business.service.impl;

import com.pk.core.business.ai.AiCandidate;
import com.pk.core.business.ai.AiRankingResult;
import com.pk.core.business.ai.AiRecommendationOrchestrator;
import com.pk.core.business.ai.AssistantReplyParser;
import com.pk.core.business.ai.GeminiAssistantClient;
import com.pk.core.business.service.AiAssistantService;
import com.pk.core.model.dto.response.AssistantMessageResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationResponseDto;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/** Trợ lý trang sức: Gemini trả lời câu tự do, món đính kèm chỉ lấy từ catalog. */
@Service
@Slf4j
public class AiAssistantChatService implements AiAssistantService {

    static final String FALLBACK_REPLY = "Xin chào, shop có thể hỗ trợ bạn chọn trang sức ạ. Bạn cần tư vấn món nào?";

    private final AiRecommendationOrchestrator orchestrator;
    private final GeminiAssistantClient gemini;

    public AiAssistantChatService(AiRecommendationOrchestrator orchestrator, GeminiAssistantClient gemini) {
        this.orchestrator = orchestrator;
        this.gemini = gemini;
    }

    @Override
    public AssistantMessageResponseDto chat(String message) {
        String text = message == null ? "" : message.trim();
        List<AiCandidate> catalog = loadCatalog();
        try {
            return reply(gemini.reply(text, catalog), catalog);
        } catch (RuntimeException ex) {
            log.warn("AI_ASSISTANT_PROVIDER_FALLBACK reason={}", ex.getMessage());
            return new AssistantMessageResponseDto(FALLBACK_REPLY, List.of());
        }
    }

    private List<AiCandidate> loadCatalog() {
        try {
            return orchestrator.openCatalog();
        } catch (RuntimeException ex) {
            log.warn("AI_ASSISTANT_CATALOG_SKIPPED reason={}", ex.getMessage());
            return List.of();
        }
    }

    private static AssistantMessageResponseDto reply(AssistantReplyParser.Draft draft, List<AiCandidate> catalog) {
        String text = draft.reply() == null || draft.reply().isBlank() ? FALLBACK_REPLY : draft.reply();
        return new AssistantMessageResponseDto(text, cards(catalog, draft.productIds()));
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

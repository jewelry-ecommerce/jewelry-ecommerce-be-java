package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.ai.AiAssembledResult;
import com.pk.core.business.ai.AiBudgetBand;
import com.pk.core.business.ai.AiDecoratedPick;
import com.pk.core.business.ai.AiRecommendationOrchestrator;
import com.pk.core.business.repository.AiStylistFeedbackRepo;
import com.pk.core.business.repository.AiStylistSessionItemRepo;
import com.pk.core.business.repository.AiStylistSessionRepo;
import com.pk.core.business.service.AiStylistService;
import com.pk.core.business.ai.AiProperties;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.StylistFeedbackRequestDto;
import com.pk.core.model.dto.request.StylistRequestDto;
import com.pk.core.model.dto.response.SetItemResponseDto;
import com.pk.core.model.dto.response.SetResponseDto;
import com.pk.core.model.dto.response.StylistFeedbackResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationListResponseDto;
import com.pk.core.model.dto.response.StylistRecommendationResponseDto;
import com.pk.core.model.dto.response.StylistSessionItemResponseDto;
import com.pk.core.model.dto.response.StylistSessionResponseDto;
import com.pk.core.model.entity.AiStylistSessionEntity;
import com.pk.core.model.entity.AiStylistSessionItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiStylistServiceImpl implements AiStylistService {

    private static final int REASON_MAX = 500;
    private static final int FALLBACK_MAX = 255;

    private final AiRecommendationOrchestrator orchestrator;
    private final AiStylistSessionRepo sessions;
    private final AiStylistSessionItemRepo sessionItems;
    private final AiStylistFeedbackRepo feedback;
    private final AiProperties properties;
    private final ObjectMapper mapper;

    @Transactional
    @Override
    public StylistRecommendationListResponseDto recommend(StylistRequestDto req, Long userId) {
        normalize(req);
        AiAssembledResult result = orchestrator.recommend(req, userId);
        Long sessionId = persist(req, userId, result);
        return new StylistRecommendationListResponseDto(result.source(), result.banner(), sessionId,
                toRecommendations(result));
    }

    @Transactional
    @Override
    public SetResponseDto buildSet(StylistRequestDto req, Long userId) {
        normalize(req);
        AiAssembledResult result = orchestrator.buildSet(req, userId);
        Long sessionId = persist(req, userId, result);
        return toSet(req, result, sessionId);
    }

    @Transactional
    @Override
    public StylistFeedbackResponseDto feedback(StylistFeedbackRequestDto req, Long userId) {
        requireUser(userId);
        if (req.getSessionId() == null || req.getProductId() == null || req.getLiked() == null) {
            throw invalidFeedback("Phản hồi thiếu phiên, sản phẩm hoặc liked");
        }
        AiStylistSessionEntity session = sessions.findOne(req.getSessionId());
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(404, ErrorCode.AI_SESSION_NOT_FOUND, "Không tìm thấy phiên gợi ý");
        }
        if (!ownsProduct(session.getId(), req.getProductId())) {
            throw invalidFeedback("Sản phẩm không thuộc phiên gợi ý");
        }
        feedback.upsert(session.getId(), userId, req.getProductId(), req.getLiked());
        return new StylistFeedbackResponseDto(session.getId(), req.getProductId(), req.getLiked());
    }

    @Transactional(readOnly = true)
    @Override
    public List<StylistSessionResponseDto> history(Long userId) {
        requireUser(userId);
        int limit = Math.max(1, properties.getHistoryLimit());
        List<AiStylistSessionEntity> rows = sessions.recent(userId, limit);
        List<StylistSessionResponseDto> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (AiStylistSessionEntity row : rows) {
            out.add(toSession(row));
        }
        return out;
    }

    private void normalize(StylistRequestDto req) {
        String scope = AiBudgetBand.scope(req);
        if (!AiBudgetBand.ITEM.equals(scope) && !AiBudgetBand.SET.equals(scope)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "budgetScope phải là ITEM hoặc SET")
                    .withField("budgetScope");
        }
        req.setBudgetScope(scope);
    }

    private Long persist(StylistRequestDto req, Long userId, AiAssembledResult result) {
        if (userId == null) {
            return null;
        }
        AiStylistSessionEntity session = newSession(req, userId, result);
        sessions.create(session);
        saveItems(session.getId(), result.picks());
        sessions.trimTo(userId, Math.max(1, properties.getHistoryLimit()));
        return session.getId();
    }

    private AiStylistSessionEntity newSession(StylistRequestDto req, Long userId, AiAssembledResult result) {
        AiStylistSessionEntity session = new AiStylistSessionEntity();
        session.setUserId(userId);
        session.setOccasion(req.getOccasion());
        session.setStylesJson(stylesJson(req.getStyle()));
        session.setBudget(req.getBudget());
        session.setBudgetScope(req.getBudgetScope());
        session.setRecipient(req.getRecipient());
        session.setSource(result.source());
        session.setFallbackReason(clip(result.fallbackReason(), FALLBACK_MAX));
        return session;
    }

    private void saveItems(Long sessionId, List<AiDecoratedPick> picks) {
        if (sessionId == null || picks == null) {
            return;
        }
        int order = 0;
        for (AiDecoratedPick pick : picks) {
            sessionItems.create(toItem(sessionId, pick, order));
            order++;
        }
    }

    private static AiStylistSessionItemEntity toItem(Long sessionId, AiDecoratedPick pick, int order) {
        AiStylistSessionItemEntity item = new AiStylistSessionItemEntity();
        item.setSessionId(sessionId);
        item.setProductId(pick.candidate().productId());
        item.setSkuId(pick.candidate().skuId());
        item.setReason(clip(pick.reason(), REASON_MAX));
        item.setMatchScore(BigDecimal.valueOf(pick.matchScore()).setScale(4, RoundingMode.HALF_UP));
        item.setSortOrder(order);
        return item;
    }

    private List<StylistRecommendationResponseDto> toRecommendations(AiAssembledResult result) {
        List<StylistRecommendationResponseDto> out = new ArrayList<>();
        if (result.picks() == null) {
            return out;
        }
        for (AiDecoratedPick pick : result.picks()) {
            out.add(toRecommendation(pick, result.source()));
        }
        return out;
    }

    private static StylistRecommendationResponseDto toRecommendation(AiDecoratedPick pick, String source) {
        return new StylistRecommendationResponseDto("PRODUCT", pick.candidate().productId(),
                pick.candidate().name(), pick.candidate().slug(), pick.candidate().thumbnailUrl(),
                pick.reason(), pick.matchScore(), pick.candidate().skuId(), pick.candidate().skuCode(),
                pick.candidate().price(), pick.candidate().stock(), source);
    }

    private SetResponseDto toSet(StylistRequestDto req, AiAssembledResult result, Long sessionId) {
        String name = "Set gợi ý cho dịp " + req.getOccasion();
        String description = result.banner() == null
                ? "Bộ trang sức gợi ý theo dịp và ngân sách"
                : result.banner();
        return new SetResponseDto("SET" + System.currentTimeMillis(), name, description, BigDecimal.ZERO,
                toSetItems(result), result.source(), result.banner(), result.totalPrice(), sessionId);
    }

    private static List<SetItemResponseDto> toSetItems(AiAssembledResult result) {
        List<SetItemResponseDto> items = new ArrayList<>();
        if (result.picks() == null) {
            return items;
        }
        int order = 0;
        for (AiDecoratedPick pick : result.picks()) {
            items.add(toSetItem(pick, result.source(), order));
            order++;
        }
        return items;
    }

    private static SetItemResponseDto toSetItem(AiDecoratedPick pick, String source, int order) {
        return new SetItemResponseDto(pick.candidate().productId(), pick.candidate().name(),
                pick.candidate().slug(), pick.candidate().thumbnailUrl(), pick.candidate().categoryName(),
                order == 0, order, pick.candidate().skuId(), pick.candidate().skuCode(),
                pick.candidate().price(), pick.candidate().stock(), source);
    }

    private boolean ownsProduct(Long sessionId, Long productId) {
        List<AiStylistSessionItemEntity> items = sessionItems.findBySessionId(sessionId);
        if (items == null) {
            return false;
        }
        for (AiStylistSessionItemEntity item : items) {
            if (productId.equals(item.getProductId())) {
                return true;
            }
        }
        return false;
    }

    private StylistSessionResponseDto toSession(AiStylistSessionEntity row) {
        return new StylistSessionResponseDto(row.getId(), row.getOccasion(), readStyles(row.getStylesJson()),
                row.getBudget(), row.getBudgetScope(), row.getRecipient(), row.getSource(),
                row.getFallbackReason(), row.getCreatedDate(), toHistoryItems(row.getId()));
    }

    private List<StylistSessionItemResponseDto> toHistoryItems(Long sessionId) {
        List<AiStylistSessionItemEntity> rows = sessionItems.findBySessionId(sessionId);
        List<StylistSessionItemResponseDto> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (AiStylistSessionItemEntity row : rows) {
            out.add(new StylistSessionItemResponseDto(row.getProductId(), row.getSkuId(), row.getReason(),
                    row.getMatchScore(), row.getSortOrder()));
        }
        return out;
    }

    private String stylesJson(List<String> styles) {
        if (styles == null || styles.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(styles);
        } catch (JsonProcessingException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "style không hợp lệ").withField("style");
        }
    }

    private List<String> readStyles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private static void requireUser(Long userId) {
        if (userId == null) {
            throw BusinessException.unauthorized(ErrorCode.UNAUTHORIZED, "Cần đăng nhập");
        }
    }

    private static BusinessException invalidFeedback(String message) {
        return BusinessException.badRequest(ErrorCode.AI_FEEDBACK_INVALID, message);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

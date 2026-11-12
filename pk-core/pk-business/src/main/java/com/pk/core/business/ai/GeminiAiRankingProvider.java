package com.pk.core.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.StylistRequestDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/** Gọi Gemini generateContent, bắt model trả JSON picks. Không ghi API key vào lỗi. */
@Component("geminiAiRankingProvider")
public class GeminiAiRankingProvider implements AiRankingProvider {

    private final AiProperties properties;
    private final RestClient client;
    private final Semaphore semaphore;
    private final ObjectMapper mapper;

    public GeminiAiRankingProvider(AiProperties properties,
                                   @Qualifier("aiRestClient") RestClient client,
                                   @Qualifier("aiProviderSemaphore") Semaphore semaphore,
                                   ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.semaphore = semaphore;
        this.mapper = mapper;
    }

    @Override
    public AiRankingResult rank(StylistRequestDto request, List<AiCandidate> candidates, boolean setMode) {
        if (!properties.providerReady()) {
            throw unavailable("AI chưa được bật");
        }
        if (!semaphore.tryAcquire()) {
            throw unavailable("AI đang bận");
        }
        try {
            return invoke(request, candidates, setMode);
        } finally {
            semaphore.release();
        }
    }

    private AiRankingResult invoke(StylistRequestDto request, List<AiCandidate> candidates, boolean setMode) {
        try {
            String raw = client.post()
                    .uri(builder -> builder.path("/models/{model}:generateContent")
                            .queryParam("key", properties.getApiKey())
                            .build(properties.getModel()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(request, candidates, setMode))
                    .retrieve()
                    .body(String.class);
            return new AiRankingResult(AiRankingResult.SOURCE_AI, null, null, parse(raw, candidates));
        } catch (RestClientException ex) {
            throw unavailable("Không gọi được AI");
        }
    }

    private Map<String, Object> body(StylistRequestDto request, List<AiCandidate> candidates, boolean setMode) {
        Map<String, Object> part = Map.of("text", prompt(request, candidates, setMode));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", List.of(part));
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("maxOutputTokens", properties.getMaxOutputTokens());
        generation.put("responseMimeType", "application/json");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(content));
        payload.put("generationConfig", generation);
        return payload;
    }

    private String prompt(StylistRequestDto request, List<AiCandidate> candidates, boolean setMode) {
        StringBuilder text = new StringBuilder();
        text.append("Bạn là stylist trang sức. Chỉ trả JSON ");
        text.append("{\"picks\":[{\"productId\":number,\"reason\":string,\"matchScore\":number}]}. ");
        text.append("Chỉ dùng productId trong danh sách. reason tiếng Việt, tối đa 160 ký tự. ");
        text.append(setMode ? "Ưu tiên món khác danh mục để thành bộ. " : "Gợi ý từng món. ");
        text.append("Dịp: ").append(request.getOccasion());
        appendBudget(text, request);
        for (AiCandidate candidate : candidates) {
            text.append("\n- id=").append(candidate.productId())
                    .append(" name=").append(candidate.name())
                    .append(" price=").append(candidate.price())
                    .append(" stock=").append(candidate.stock())
                    .append(" category=").append(candidate.categoryName());
        }
        int max = Math.max(500, properties.getMaxInputChars());
        return text.length() <= max ? text.toString() : text.substring(0, max);
    }

    private static void appendBudget(StringBuilder text, StylistRequestDto request) {
        if (request.getBudget() == null) {
            return;
        }
        text.append(". Ngân sách: ").append(request.getBudget()).append(" VND, phạm vi ")
                .append(AiBudgetBand.scope(request));
        if (request.getRecipient() != null && !request.getRecipient().isBlank()) {
            text.append(", tặng ").append(request.getRecipient().trim());
        }
    }

    private List<AiRankedPick> parse(String raw, List<AiCandidate> candidates) {
        try {
            String text = firstText(mapper.readTree(raw));
            if (text == null || text.isBlank()) {
                throw unavailable("AI không trả nội dung");
            }
            return readPicks(unwrap(text), allowedIds(candidates));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("AI trả về không đúng định dạng");
        }
    }

    private static String firstText(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }
        return parts.get(0).path("text").asText(null);
    }

    private List<AiRankedPick> readPicks(String json, Set<Long> allowed) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode picks = root.has("picks") ? root.get("picks") : root;
        if (picks == null || !picks.isArray()) {
            throw unavailable("AI trả về không đúng định dạng");
        }
        List<AiRankedPick> result = new ArrayList<>();
        for (JsonNode node : picks) {
            AiRankedPick pick = toPick(node, allowed);
            if (pick != null) {
                result.add(pick);
            }
        }
        return result;
    }

    private static AiRankedPick toPick(JsonNode node, Set<Long> allowed) {
        if (node == null || !node.hasNonNull("productId")) {
            return null;
        }
        long productId = node.get("productId").asLong();
        if (!allowed.contains(productId)) {
            return null;
        }
        String reason = node.path("reason").asText("Phù hợp nhu cầu của bạn");
        double score = node.path("matchScore").asDouble(0.5);
        double clamped = Math.max(0d, Math.min(1d, score));
        return new AiRankedPick(productId, clip(reason, 160), clamped);
    }

    private static Set<Long> allowedIds(List<AiCandidate> candidates) {
        return candidates.stream().map(AiCandidate::productId).collect(Collectors.toSet());
    }

    private static String unwrap(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int start = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        if (start < 0 || end <= start) {
            return trimmed;
        }
        return trimmed.substring(start + 1, end).trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(503, ErrorCode.AI_PROVIDER_UNAVAILABLE, message);
    }
}

package com.pk.core.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/** Gemini trả lời câu chat. Chỉ được nhắc sản phẩm có trong catalog. */
@Component
public class GeminiAssistantClient {

    private final AiProperties properties;
    private final RestClient client;
    private final Semaphore semaphore;
    private final AssistantReplyParser parser;
    private final ObjectMapper mapper;

    public GeminiAssistantClient(AiProperties properties,
                                 @Qualifier("aiRestClient") RestClient client,
                                 @Qualifier("aiProviderSemaphore") Semaphore semaphore,
                                 ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.semaphore = semaphore;
        this.parser = new AssistantReplyParser(mapper);
        this.mapper = mapper;
    }

    public AssistantReplyParser.Draft reply(String message, List<AiCandidate> catalog) {
        if (!properties.providerReady()) {
            throw unavailable("AI chưa được bật");
        }
        if (!semaphore.tryAcquire()) {
            throw unavailable("AI đang bận");
        }
        try {
            return invoke(message, catalog);
        } finally {
            semaphore.release();
        }
    }

    private AssistantReplyParser.Draft invoke(String message, List<AiCandidate> catalog) {
        try {
            String raw = client.post()
                    .uri(builder -> builder.path("/models/{model}:generateContent")
                            .queryParam("key", properties.getApiKey())
                            .build(properties.getModel()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(message, catalog))
                    .retrieve()
                    .body(String.class);
            return parser.parse(firstText(mapper.readTree(raw)), allowed(catalog));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable("Không gọi được AI");
        } catch (Exception ex) {
            throw unavailable("AI trả về không đúng định dạng");
        }
    }

    private Map<String, Object> body(String message, List<AiCandidate> catalog) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", List.of(Map.of("text", prompt(message, catalog))));
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("maxOutputTokens", properties.getMaxOutputTokens());
        generation.put("responseMimeType", "application/json");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(content));
        payload.put("generationConfig", generation);
        return payload;
    }

    private String prompt(String message, List<AiCandidate> catalog) {
        StringBuilder text = new StringBuilder();
        text.append("Bạn là nhân viên shop trang sức. Xưng shop, gọi khách là bạn, lịch sự, có chữ ạ. ");
        text.append("Shop chào trước. Không bảo khách chào shop. ");
        text.append("Trả lời tiếng Việt, ngắn. Không dùng các từ trợ lý, stylist, chat, AI. ");
        text.append("Khách có thể chào hoặc hỏi về dịp, chất liệu, cách giữ gìn, kích cỡ và quà tặng. ");
        text.append("Chỉ trả JSON {\"reply\":string,\"productIds\":[number]}. ");
        text.append("productIds chỉ lấy id trong danh sách, tối đa 4, để trống nếu câu không cần gợi ý món. ");
        text.append("Không bịa sản phẩm ngoài danh sách.\nKhách: ").append(message);
        for (AiCandidate candidate : catalog) {
            text.append("\n- id=").append(candidate.productId())
                    .append(" name=").append(candidate.name())
                    .append(" price=").append(candidate.price())
                    .append(" category=").append(candidate.categoryName());
        }
        int max = Math.max(500, properties.getMaxInputChars());
        return text.length() <= max ? text.toString() : text.substring(0, max);
    }

    private static String firstText(JsonNode root) {
        JsonNode parts = root == null ? null : root.path("candidates").path(0).path("content").path("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            return "";
        }
        return parts.get(0).path("text").asText("");
    }

    private static Set<Long> allowed(List<AiCandidate> catalog) {
        return catalog.stream().map(AiCandidate::productId).collect(Collectors.toSet());
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(503, ErrorCode.AI_PROVIDER_UNAVAILABLE, message);
    }
}

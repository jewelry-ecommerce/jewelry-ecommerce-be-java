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
                                 @Qualifier("assistantRestClient") RestClient client,
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
                    .uri(GeminiGenerateEndpoint.uri(properties.getBaseUrl(), model()))
                    .header(GeminiGenerateEndpoint.API_KEY_HEADER, properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(message, catalog))
                    .retrieve()
                    .body(String.class);
            return readReply(raw, catalog);
        } catch (BusinessException ex) {
            throw ex;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            throw unavailable("Không gọi được AI " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw unavailable("Không gọi được AI");
        } catch (Exception ex) {
            throw unavailable("AI trả về không đúng định dạng");
        }
    }

    private AssistantReplyParser.Draft readReply(String raw, List<AiCandidate> catalog) throws Exception {
        String text = firstText(mapper.readTree(raw));
        if (text.isBlank()) {
            throw unavailable("AI không có nội dung trả lời");
        }
        try {
            return parser.parse(text, allowed(catalog));
        } catch (Exception ex) {
            return new AssistantReplyParser.Draft(text, List.of());
        }
    }

    private Map<String, Object> body(String message, List<AiCandidate> catalog) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", List.of(Map.of("text", prompt(message, catalog))));
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("maxOutputTokens", properties.getAssistantMaxOutputTokens());
        generation.put("thinkingConfig", Map.of("thinkingLevel", properties.getAssistantThinkingLevel()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(content));
        payload.put("generationConfig", generation);
        return payload;
    }

    private String prompt(String message, List<AiCandidate> catalog) {
        StringBuilder text = new StringBuilder();
        text.append(role());
        text.append(properties.getShopBrief() == null ? "" : properties.getShopBrief());
        text.append(catalog.isEmpty() ? emptyCatalog() : " Chỉ gợi ý id trong danh sách dưới.");
        text.append("\nKhách: ").append(message);
        appendProducts(text, catalog);
        int max = Math.max(500, properties.getMaxInputChars());
        return text.length() <= max ? text.toString() : text.substring(0, max);
    }

    private static String role() {
        return "Bạn là nhân viên shop trang sức. Xưng shop, gọi khách là bạn, lịch sự, có chữ ạ. "
                + "Trả lời tiếng Việt, ngắn. Không dùng các từ trợ lý, stylist, chat, AI. "
                + "Chỉ trả JSON {\"reply\":string,\"productIds\":[number]}. "
                + "productIds tối đa 4, để trống nếu câu không cần gợi ý món. ";
    }

    private static String emptyCatalog() {
        return " Không có sản phẩm khớp câu này. Không bịa tên hoặc giá. "
                + "Câu chào hoặc câu về shop thì trả lời theo tờ thông tin, productIds để trống.";
    }

    private static void appendProducts(StringBuilder text, List<AiCandidate> catalog) {
        for (AiCandidate candidate : catalog) {
            text.append("\n- id=").append(candidate.productId())
                    .append(" name=").append(candidate.name())
                    .append(" price=").append(candidate.price())
                    .append(" category=").append(candidate.categoryName())
                    .append(" material=").append(candidate.material())
                    .append(" stock=").append(candidate.stock());
        }
    }

    private String model() {
        return properties.chatModel();
    }

    private static String firstText(JsonNode root) {
        JsonNode parts = root == null ? null : root.path("candidates").path(0).path("content").path("parts");
        if (parts == null || !parts.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        return text.toString();
    }

    private static Set<Long> allowed(List<AiCandidate> catalog) {
        return catalog.stream().map(AiCandidate::productId).collect(Collectors.toSet());
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(503, ErrorCode.AI_PROVIDER_UNAVAILABLE, message);
    }
}

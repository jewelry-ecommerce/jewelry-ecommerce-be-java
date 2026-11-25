package com.pk.core.business.ai.tryon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.ai.AiProperties;
import com.pk.core.business.ai.GeminiGenerateEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Vision analyze qua Chat Completions (cùng đường chat đang chạy trên Render).
 * Lookalike image vẫn dùng {@code generateContent} + fallback thumbnail nếu fail.
 */
@Slf4j
@Component
public class GeminiTryOnClient {

    private static final int ANALYZE_MAX_OUTPUT_TOKENS = 4096;

    private final AiProperties properties;
    private final RestClient client;
    private final Semaphore semaphore;
    private final ObjectMapper mapper;

    public GeminiTryOnClient(AiProperties properties,
                             @Qualifier("tryOnRestClient") RestClient client,
                             @Qualifier("aiProviderSemaphore") Semaphore semaphore,
                             ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.semaphore = semaphore;
        this.mapper = mapper;
    }

    public TryOnAnalysis analyze(byte[] imageBytes, String mimeType, String styleHint) {
        if (!semaphore.tryAcquire()) {
            return new TryOnAnalysis(List.of(), List.of(), "AI đang bận, thử lại sau");
        }
        try {
            String text = chatVisionText(imageBytes, mimeType, styleHint);
            return parseAnalysisText(text);
        } catch (RestClientResponseException ex) {
            log.warn("try-on analyze HTTP {} {}", ex.getStatusCode().value(), briefBody(ex.getResponseBodyAsString()));
            return new TryOnAnalysis(List.of(), List.of(),
                    "Không phân tích được ảnh (HTTP " + ex.getStatusCode().value() + "), thử lại sau");
        } catch (RuntimeException ex) {
            log.warn("try-on analyze failed: {}", ex.toString());
            return new TryOnAnalysis(List.of(), List.of(), "Không phân tích được ảnh, thử lại sau");
        } finally {
            semaphore.release();
        }
    }

    /** Trả PNG bytes; null nếu model không sinh ảnh. */
    public byte[] generateLookalike(byte[] userImage, String userMime,
                                    byte[] productImage, String productMime,
                                    String productName, String style, String region) {
        if (!semaphore.tryAcquire()) {
            return null;
        }
        try {
            String raw = post(properties.tryOnImageModelName(),
                    generateBody(userImage, userMime, productImage, productMime, productName, style, region));
            return firstImageBytes(raw);
        } catch (RestClientResponseException ex) {
            log.warn("try-on generate HTTP {} {}", ex.getStatusCode().value(), briefBody(ex.getResponseBodyAsString()));
            return null;
        } catch (RuntimeException ex) {
            log.warn("try-on generate failed: {}", ex.toString());
            return null;
        } finally {
            semaphore.release();
        }
    }

    private String chatVisionText(byte[] imageBytes, String mimeType, String styleHint) {
        String raw = client.post()
                .uri(chatCompletionsUri())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(chatVisionBody(imageBytes, mimeType, styleHint))
                .retrieve()
                .body(String.class);
        try {
            JsonNode content = mapper.readTree(raw).path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new IllegalStateException("empty chat vision content");
            }
            return content.asText();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("invalid chat vision response", ex);
        }
    }

    private Map<String, Object> chatVisionBody(byte[] imageBytes, String mimeType, String styleHint) {
        String mime = mimeType == null || mimeType.isBlank() ? "image/jpeg" : mimeType;
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", analyzePrompt(styleHint)),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.chatModel());
        payload.put("temperature", properties.getTemperature());
        payload.put("max_tokens", ANALYZE_MAX_OUTPUT_TOKENS);
        payload.put("messages", List.of(Map.of("role", "user", "content", content)));
        return payload;
    }

    private URI chatCompletionsUri() {
        String base = properties.getChatBaseUrl() == null ? "" : properties.getChatBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/chat/completions");
    }

    private String post(String model, Map<String, Object> body) {
        return client.post()
                .uri(GeminiGenerateEndpoint.uri(properties.getBaseUrl(), model))
                .header(GeminiGenerateEndpoint.API_KEY_HEADER, properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private Map<String, Object> generateBody(byte[] userImage, String userMime,
                                             byte[] productImage, String productMime,
                                             String productName, String style, String region) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", generatePrompt(productName, style, region)));
        parts.add(inlinePart(userMime, userImage));
        parts.add(inlinePart(productMime, productImage));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", parts);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(content));
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("responseModalities", List.of("TEXT", "IMAGE"));
        payload.put("generationConfig", generation);
        return payload;
    }

    private static Map<String, Object> inlinePart(String mimeType, byte[] bytes) {
        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("mimeType", mimeType == null || mimeType.isBlank() ? "image/jpeg" : mimeType);
        inline.put("data", Base64.getEncoder().encodeToString(bytes));
        return Map.of("inlineData", inline);
    }

    private static String analyzePrompt(String styleHint) {
        StringBuilder text = new StringBuilder();
        text.append("Bạn là stylist trang sức. Phân tích ảnh người dùng. ");
        text.append("Chỉ trả JSON {\"regions\":[string],\"styles\":[string],\"rejectReason\":string|null}. ");
        text.append("regions chỉ dùng: wrist,ankle,neck,ear,finger,hand_combo. ");
        text.append("styles chỉ dùng: minimal,statement,everyday (chọn 2 hoặc 3). ");
        text.append("Nếu không thấy cổ tay/cổ chân/cổ/tai/ngón tay rõ → regions=[] và rejectReason tiếng Việt. ");
        if (styleHint != null && !styleHint.isBlank()) {
            text.append("Gợi ý phong cách khách: ").append(styleHint.trim()).append(". ");
        }
        return text.toString();
    }

    private static String generatePrompt(String productName, String style, String region) {
        return "Tạo ảnh minh họa try-on trang sức lookalike. Giữ nguyên tay/cảnh/ánh sáng ảnh người dùng (ảnh 1). "
                + "Chỉ ghép món trang sức giống ảnh sản phẩm (ảnh 2) lên đúng vùng " + region + ". "
                + "Phong cách: " + style + ". Tên SP tham chiếu: " + productName + ". "
                + "Không thêm chữ watermark. Không thay đổi khuôn mặt nếu có.";
    }

    private TryOnAnalysis parseAnalysisText(String text) {
        try {
            if (text == null || text.isBlank()) {
                return new TryOnAnalysis(List.of(), List.of(), "AI không trả kết quả phân tích");
            }
            JsonNode root = mapper.readTree(unwrap(text));
            List<String> regions = readStringList(root.get("regions"));
            List<String> styles = readStringList(root.get("styles"));
            String reject = textOrNull(root.get("rejectReason"));
            List<String> cleanRegions = regions.stream().filter(TryOnBodyRegion::supported).distinct().toList();
            List<String> cleanStyles = styles.isEmpty() ? TryOnBodyRegion.STYLES : styles.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .filter(TryOnBodyRegion.STYLES::contains)
                    .distinct()
                    .toList();
            if (cleanRegions.isEmpty()) {
                String reason = reject == null || reject.isBlank()
                        ? "Không nhận diện được bộ phận đeo trang sức trên ảnh"
                        : reject;
                return new TryOnAnalysis(List.of(), cleanStyles, reason);
            }
            return new TryOnAnalysis(cleanRegions, cleanStyles.isEmpty() ? TryOnBodyRegion.STYLES : cleanStyles, null);
        } catch (Exception ex) {
            return new TryOnAnalysis(List.of(), List.of(), "Không đọc được kết quả phân tích ảnh");
        }
    }

    private byte[] firstImageBytes(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return null;
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray()) {
                return null;
            }
            for (JsonNode part : parts) {
                JsonNode inline = part.has("inlineData") ? part.get("inlineData") : part.get("inline_data");
                if (inline == null || !inline.has("data")) {
                    continue;
                }
                return Base64.getDecoder().decode(inline.get("data").asText());
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String briefBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200);
    }

    private static String unwrap(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }
}

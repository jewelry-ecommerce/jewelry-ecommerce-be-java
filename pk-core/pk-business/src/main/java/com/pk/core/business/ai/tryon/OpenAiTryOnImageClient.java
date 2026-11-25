package com.pk.core.business.ai.tryon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pk.core.business.ai.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Lookalike try-on qua OpenAI Images Edit (JSON + data URL) — trả phí, demo không bị free-tier 429.
 */
@Slf4j
@Component
public class OpenAiTryOnImageClient {

    private final AiProperties properties;
    private final Semaphore semaphore;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiTryOnImageClient(AiProperties properties,
                                  @org.springframework.beans.factory.annotation.Qualifier("aiProviderSemaphore")
                                  Semaphore semaphore,
                                  ObjectMapper mapper) {
        this.properties = properties;
        this.semaphore = semaphore;
        this.mapper = mapper;
        Duration timeout = properties.getTryOnTimeout() == null ? Duration.ofSeconds(90) : properties.getTryOnTimeout();
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public byte[] generateLookalike(byte[] userImage, String userMime,
                                    byte[] productImage, String productMime,
                                    String productName, String style, String region,
                                    String[] errorOut) {
        if (!properties.tryOnOpenAiReady()) {
            setError(errorOut, "Thiếu OPENAI_API_KEY cho try-on image");
            return null;
        }
        if (!acquire(errorOut)) {
            return null;
        }
        try {
            return editImages(userImage, userMime, productImage, productMime, productName, style, region, errorOut);
        } finally {
            semaphore.release();
        }
    }

    private boolean acquire(String[] errorOut) {
        try {
            if (!semaphore.tryAcquire(60, TimeUnit.SECONDS)) {
                setError(errorOut, "AI đang bận (hết slot)");
                return false;
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            setError(errorOut, "AI bị gián đoạn");
            return false;
        }
    }

    private byte[] editImages(byte[] userImage, String userMime,
                              byte[] productImage, String productMime,
                              String productName, String style, String region,
                              String[] errorOut) {
        try {
            String json = buildPayload(userImage, userMime, productImage, productMime, productName, style, region);
            Duration timeout = properties.getTryOnTimeout() == null ? Duration.ofSeconds(90) : properties.getTryOnTimeout();
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(properties.getOpenAiBaseUrl()) + "/images/edits"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + properties.getOpenAiApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("openai try-on HTTP {} {}", response.statusCode(), brief(response.body()));
                setError(errorOut, "OpenAI images/edits HTTP " + response.statusCode());
                return null;
            }
            return firstB64Image(response.body(), errorOut);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("openai try-on failed: {}", ex.toString());
            setError(errorOut, "OpenAI images/edits lỗi mạng");
            return null;
        }
    }

    private String buildPayload(byte[] userImage, String userMime,
                                byte[] productImage, String productMime,
                                String productName, String style, String region) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", properties.tryOnImageModelName());
        root.put("prompt", prompt(productName, style, region));
        root.put("quality", properties.tryOnImageQualityName());
        root.put("complexity", properties.tryOnImageComplexityName());
        root.put("size", "1024x1024");
        root.put("output_format", "jpeg");
        ArrayNode images = root.putArray("images");
        images.addObject().put("image_url", dataUrl(userMime, userImage));
        if (productImage != null && productImage.length > 0) {
            images.addObject().put("image_url", dataUrl(productMime, productImage));
        }
        return mapper.writeValueAsString(root);
    }

    private byte[] firstB64Image(String raw, String[] errorOut) throws IOException {
        JsonNode data = mapper.readTree(raw).path("data");
        if (!data.isArray() || data.isEmpty()) {
            setError(errorOut, "OpenAI không trả ảnh");
            return null;
        }
        String b64 = data.get(0).path("b64_json").asText(null);
        if (b64 == null || b64.isBlank()) {
            setError(errorOut, "OpenAI response thiếu b64_json");
            return null;
        }
        return Base64.getDecoder().decode(b64);
    }

    private static String prompt(String productName, String style, String region) {
        return "Photorealistic jewelry virtual try-on. Image 1 is the customer's photo — keep the same person, "
                + "pose, background, and lighting. Composite a realistic jewelry piece onto region: " + region + ". "
                + "If image 2 is provided, match that product closely; otherwise invent a tasteful " + style
                + " piece inspired by \"" + productName + "\". Jewelry must look worn on the body, not a product shot. "
                + "No text, watermark, logo, or border.";
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime(mime) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String mime(String value) {
        return value == null || value.isBlank() ? "image/jpeg" : value;
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static void setError(String[] errorOut, String message) {
        if (errorOut != null && errorOut.length > 0 && message != null && !message.isBlank()) {
            errorOut[0] = message;
        }
    }

    private static String brief(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String one = body.replaceAll("\\s+", " ").trim();
        return one.length() <= 200 ? one : one.substring(0, 200);
    }
}

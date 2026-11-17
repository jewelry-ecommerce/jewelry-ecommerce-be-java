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
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Chat Completions dùng chung cho OpenAI, LiteLLM và endpoint OpenAI của Gemini. */
@Component
public class AssistantCompletionClient {

    private final AiProperties properties;
    private final RestClient client;
    private final Semaphore semaphore;
    private final ObjectMapper mapper;

    public AssistantCompletionClient(AiProperties properties,
                                     @Qualifier("assistantRestClient") RestClient client,
                                     @Qualifier("aiProviderSemaphore") Semaphore semaphore,
                                     ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.semaphore = semaphore;
        this.mapper = mapper;
    }

    public JsonNode complete(List<Map<String, Object>> messages, boolean allowTools) {
        if (!properties.providerReady()) {
            throw unavailable("AI chưa được bật");
        }
        if (!semaphore.tryAcquire()) {
            throw unavailable("AI đang bận");
        }
        try {
            return message(post(body(messages, allowTools)));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw httpFailure(ex);
        } catch (RestClientException ex) {
            throw unavailable("Không gọi được AI");
        } catch (Exception ex) {
            throw unavailable("AI trả về không đúng định dạng");
        } finally {
            semaphore.release();
        }
    }

    private String post(Map<String, Object> body) {
        try {
            return send(body);
        } catch (RestClientResponseException ex) {
            Map<String, Object> retry = relax(body);
            if (ex.getStatusCode().value() != 400 || retry.equals(body)) {
                throw httpFailure(ex);
            }
            return resend(retry);
        }
    }

    private String resend(Map<String, Object> body) {
        try {
            return send(body);
        } catch (RestClientResponseException ex) {
            throw httpFailure(ex);
        }
    }

    private static Map<String, Object> relax(Map<String, Object> body) {
        Map<String, Object> retry = new LinkedHashMap<>(body);
        retry.remove("prompt_cache_key");
        retry.remove("prompt_cache_retention");
        retry.remove("parallel_tool_calls");
        retry.remove("response_format");
        return retry;
    }

    private String send(Map<String, Object> body) {
        return client.post()
                .uri(endpoint())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private Map<String, Object> body(List<Map<String, Object>> messages, boolean allowTools) {
        boolean gemini = geminiHost(properties.getChatBaseUrl());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.chatModel());
        payload.put("temperature", properties.getTemperature());
        payload.put("messages", messages);
        payload.put("max_tokens", properties.getAssistantMaxOutputTokens());
        if (allowTools) {
            payload.put("tools", AssistantTools.definitions(!gemini));
            payload.put("tool_choice", "auto");
            if (!gemini) {
                payload.put("parallel_tool_calls", false);
            }
        } else {
            payload.put("response_format", AssistantTools.replySchema(!gemini));
        }
        if (!gemini) {
            putCache(payload);
        }
        return payload;
    }

    public static boolean geminiHost(String chatBaseUrl) {
        return chatBaseUrl != null && chatBaseUrl.contains("generativelanguage.googleapis.com");
    }

    private void putCache(Map<String, Object> payload) {
        String key = properties.getPromptCacheKey();
        if (key == null || key.isBlank()) {
            return;
        }
        payload.put("prompt_cache_key", key);
        payload.put("prompt_cache_retention", properties.getPromptCacheRetention());
    }

    private JsonNode message(String raw) throws Exception {
        JsonNode message = mapper.readTree(raw).path("choices").path(0).path("message");
        if (message.isMissingNode() || message.isNull()) {
            throw unavailable("AI không có nội dung trả lời");
        }
        return message;
    }

    private URI endpoint() {
        String base = properties.getChatBaseUrl() == null ? "" : properties.getChatBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/chat/completions");
    }

    private static BusinessException httpFailure(RestClientResponseException ex) {
        String detail = brief(ex.getResponseBodyAsString());
        String message = detail.isBlank()
                ? "Không gọi được AI " + ex.getStatusCode().value()
                : "Không gọi được AI " + ex.getStatusCode().value() + ": " + detail;
        return unavailable(message);
    }

    private static String brief(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160);
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(503, ErrorCode.AI_PROVIDER_UNAVAILABLE, message);
    }
}

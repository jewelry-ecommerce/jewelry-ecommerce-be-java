package com.pk.core.business.ai;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** URL generateContent giữ nguyên dấu hai chấm. UriBuilder mã hóa dấu này thành %3A và Google trả 404. */
final class GeminiGenerateEndpoint {

    private GeminiGenerateEndpoint() {
    }

    static URI uri(String baseUrl, String model, String apiKey) {
        String key = URLEncoder.encode(apiKey == null ? "" : apiKey, StandardCharsets.UTF_8);
        return URI.create(trimSlash(baseUrl) + "/models/" + model + ":generateContent?key=" + key);
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}

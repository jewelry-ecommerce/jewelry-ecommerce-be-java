package com.pk.core.business.ai;

import java.net.URI;

/**
 * URL {@code generateContent} giữ nguyên dấu hai chấm trong path.
 * UriBuilder mã hóa {@code :} thành {@code %3A} và Google trả 404.
 * Auth qua header {@code x-goog-api-key} only.
 * Không dùng {@code Authorization: Bearer} với API key — endpoint này hiểu Bearer là OAuth → 401.
 */
public final class GeminiGenerateEndpoint {

    public static final String API_KEY_HEADER = "x-goog-api-key";

    private GeminiGenerateEndpoint() {
    }

    public static URI uri(String baseUrl, String model) {
        return URI.create(trimSlash(baseUrl) + "/models/" + model + ":generateContent");
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}

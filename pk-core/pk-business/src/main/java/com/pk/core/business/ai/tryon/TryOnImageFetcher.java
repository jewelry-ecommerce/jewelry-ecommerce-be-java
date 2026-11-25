package com.pk.core.business.ai.tryon;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Tải thumbnail SP từ URL công khai để đưa vào prompt gen ảnh. */
@Component
public class TryOnImageFetcher {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Fetched fetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            byte[] bytes = response.body().readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            String mime = mimeFrom(response.headers().firstValue("Content-Type").orElse(""), url);
            return new Fetched(bytes, mime);
        } catch (IOException | InterruptedException | IllegalArgumentException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String mimeFrom(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (ct.contains("png")) {
            return "image/png";
        }
        if (ct.contains("webp")) {
            return "image/webp";
        }
        if (ct.contains("gif")) {
            return "image/gif";
        }
        String lower = url.toLowerCase();
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public record Fetched(byte[] bytes, String mimeType) {
    }
}

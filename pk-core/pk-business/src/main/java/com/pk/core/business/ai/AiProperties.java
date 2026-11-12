package com.pk.core.business.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/** Cấu hình AI Stylist tập trung. Secret chỉ qua env, không trả về admin/FE. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_NONE = "none";

    /** Bật gọi provider; false hoặc thiếu key → fallback rule-based. */
    private boolean enabled = false;

    /** gemini | none */
    private String provider = PROVIDER_GEMINI;

    private String apiKey = "";

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /** Model miễn phí phù hợp đồ án; có thể đổi bằng env. */
    private String model = "gemini-2.0-flash";

    /** Timeout cuộc gọi provider (spec EP-05 ~3s). */
    private Duration timeout = Duration.ofSeconds(3);

    private int maxCandidates = 24;

    private int minRecommendations = 3;

    private int maxRecommendations = 6;

    private int minSetItems = 2;

    private int maxSetItems = 6;

    private int historyLimit = 20;

    /** Dung sai ngân sách ±10% theo BR-AI-02. */
    private BigDecimal budgetToleranceRatio = new BigDecimal("0.10");

    private int maxConcurrentCalls = 2;

    private int maxOutputTokens = 1024;

    private int maxInputChars = 6000;

    public boolean providerReady() {
        return enabled
                && PROVIDER_GEMINI.equalsIgnoreCase(provider)
                && apiKey != null
                && !apiKey.isBlank();
    }
}

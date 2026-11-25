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

    /** Model xếp hạng stylist. Chat dùng assistantModel. */
    private String model = "gemini-3.5-flash-lite";

    /** Model chat. Bản Pro, câu tư vấn chắc hơn flash-lite. */
    private String assistantModel = "gemini-3.1-pro-preview";

    /** Model còn mở khi env vẫn trỏ gemini-2.* (Google trả 404). */
    private String currentModel = "gemini-3.1-pro-preview";

    /** Độ suy nghĩ của chat. HIGH để câu tư vấn chắc hơn. */
    private String assistantThinkingLevel = "HIGH";

    /** Trần token câu chat. HIGH cần dư chỗ cho phần suy nghĩ và câu trả lời. */
    private int assistantMaxOutputTokens = 8192;

    /** Timeout cuộc gọi xếp hạng (spec EP-05 ~3s). */
    private Duration timeout = Duration.ofSeconds(3);

    /** Chat Pro mức HIGH có thể mất gần một phút. */
    private Duration assistantTimeout = Duration.ofSeconds(60);

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

    /** Số món một lần function search_products được trả. */
    private int assistantRetrievalLimit = 8;

    /** Chat Completions kiểu OpenAI. LiteLLM proxy dùng cùng đường này. */
    private String chatBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";

    /** Câu hỗ trợ khách cần ổn định. OpenAI khuyên khoảng 0.2, không phải 1. */
    private double temperature = 0.2;

    /** Cùng prefix system + tool thì cùng key để cache routing. */
    private String promptCacheKey = "jewelry-shop-assistant";

    private String promptCacheRetention = "in_memory";

    /** Vòng gọi tool trước câu trả lời cuối. */
    private int assistantMaxToolRounds = 1;

    /** Số lượt gần nhất đưa vào request. Một lượt là một câu khách và một câu shop. */
    private int assistantContextTurns = 10;

    /** Tờ thông tin shop, nằm trong bộ nhớ. Câu phí ship và thanh toán không query sản phẩm. */
    private String shopBrief = "Shop bán trang sức. Khách xem sản phẩm, thêm vào giỏ rồi đặt hàng. "
            + "Thanh toán COD hoặc PayOS. Phí giao hàng hiện là 0 đồng. "
            + "Khách đăng nhập để xem đơn. Shop không bịa thời gian đổi trả.";

    /**
     * Provider gen ảnh try-on: {@code openai} (trả phí, ổn demo) | {@code gemini}.
     * Analyze vẫn dùng Gemini chat.
     */
    private String tryOnImageProvider = "openai";

    /** OpenAI API key — bắt buộc khi tryOnImageProvider=openai. */
    private String openAiApiKey = "";

    private String openAiBaseUrl = "https://api.openai.com/v1";

    /**
     * Model gen ảnh. OpenAI mặc định {@code gpt-image-2.6-sunburst};
     * Gemini mặc định {@code gemini-3.1-flash-image}.
     */
    private String tryOnImageModel = "gpt-image-2.6-sunburst";

    /** Timeout analyze + gen ảnh (dài hơn ranking text). */
    private Duration tryOnTimeout = Duration.ofSeconds(120);

    /** Số mẫu tối đa mỗi lần upload. OpenAI paid: 3 để demo thoải mái. */
    private int tryOnMaxVariants = 3;

    /** Dung lượng ảnh upload tối đa (bytes). */
    private long tryOnMaxUploadBytes = 5_242_880L;

    public boolean providerReady() {
        return enabled
                && PROVIDER_GEMINI.equalsIgnoreCase(provider)
                && apiKey != null
                && !apiKey.isBlank();
    }

    public String chatModel() {
        return usable(assistantModel, model);
    }

    public String rankingModel() {
        return usable(model, currentModel);
    }

    public String tryOnVisionModel() {
        return rankingModel();
    }

    public boolean tryOnOpenAiReady() {
        return tryOnImageUsesOpenAi() && openAiApiKey != null && !openAiApiKey.isBlank();
    }

    public boolean tryOnImageUsesOpenAi() {
        return "openai".equalsIgnoreCase(tryOnImageProvider == null ? "" : tryOnImageProvider.trim());
    }

    public String tryOnImageModelName() {
        if (!blank(tryOnImageModel)) {
            return tryOnImageModel.trim();
        }
        return tryOnImageUsesOpenAi() ? "gpt-image-2.6-sunburst" : "gemini-3.1-flash-image";
    }

    private String usable(String preferred, String alternate) {
        String chosen = blank(preferred) ? alternate : preferred.trim();
        if (chosen != null && chosen.startsWith("gemini-2.")) {
            return blank(currentModel) ? chosen : currentModel.trim();
        }
        return chosen;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

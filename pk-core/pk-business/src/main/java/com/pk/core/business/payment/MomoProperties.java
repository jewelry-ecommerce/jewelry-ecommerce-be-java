package com.pk.core.business.payment;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.stream.Stream;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.momo")
public class MomoProperties {
    public static final String PROVIDER = "MOMO";
    public static final String API_BASE = "https://test-payment.momo.vn";
    public static final String REQUEST_TYPE = "captureWallet";
    public static final long MIN_AMOUNT = 1000;
    public static final long MAX_AMOUNT = 50000000;
    private boolean enabled;
    private String partnerCode = "";
    private String accessKey = "";
    private String secretKey = "";
    private String redirectUrl = "";
    private String ipnUrl = "";
    private Duration timeout = Duration.ofSeconds(30);

    public boolean ready() {
        return enabled && Stream.of(partnerCode, accessKey, secretKey, redirectUrl, ipnUrl)
                .allMatch(value -> value != null && !value.isBlank())
                && redirectUrl.startsWith("https://") && ipnUrl.startsWith("https://");
    }
    public void requireReady() {
        if (!ready()) throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED,
                "MoMo Sandbox chưa được cấu hình. Vui lòng chọn COD.");
        if (!redirectUrl.startsWith("https://") || !ipnUrl.startsWith("https://")) {
            throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED, "MoMo yêu cầu URL HTTPS được cấu hình trên server.");
        }
    }
    public void validateMethod(String method) {
        if ("COD".equals(method)) return;
        if (PROVIDER.equals(method)) { requireReady(); return; }
        throw BusinessException.badRequest(ErrorCode.PAYMENT_METHOD_UNSUPPORTED, "Chỉ hỗ trợ COD hoặc MoMo Sandbox.");
    }
    public void validateAmount(String method, BigDecimal amount) {
        if (!PROVIDER.equals(method)) return;
        try {
            long value = amount.longValueExact();
            if (value >= MIN_AMOUNT && value <= MAX_AMOUNT) return;
        } catch (ArithmeticException failure) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "MoMo yêu cầu số tiền nguyên đồng.");
        }
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "MoMo hỗ trợ từ 1.000 đến 50.000.000 đồng.");
    }
}

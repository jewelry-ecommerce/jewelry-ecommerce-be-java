package com.pk.core.business.payment;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.payos")
public class PayosProperties {
    public static final String PROVIDER = "PAYOS";
    public static final long MAX_AMOUNT = 50_000_000L;
    public static final int DESCRIPTION_MAX = 9;

    private boolean enabled = false;
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    private String endpoint = "https://api-merchant.payos.vn/v2/payment-requests";
    private String returnUrl = "http://localhost:8080/api/v1/storefront/payment/payos/return";
    private String cancelUrl = "http://localhost:8080/api/v1/storefront/payment/payos/cancel";
    private String clientBaseUrl = "http://localhost:3100";

    public String canonical(String method) {
        validateMethod(method);
        return method != null && PROVIDER.equalsIgnoreCase(method.trim()) ? PROVIDER : "COD";
    }

    public void validateMethod(String method) {
        if (method == null || method.isBlank() || "COD".equalsIgnoreCase(method.trim())) return;
        if (PROVIDER.equalsIgnoreCase(method.trim())) {
            requireReady();
            return;
        }
        throw BusinessException.badRequest(ErrorCode.PAYMENT_METHOD_UNSUPPORTED, "Chỉ hỗ trợ COD hoặc PayOS.");
    }

    public void validateAmount(String method, BigDecimal amount) {
        if (!PROVIDER.equals(method) || wholeVnd(amount)) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "PayOS yêu cầu số tiền nguyên đồng lớn hơn 0.");
    }

    public void requireReady() {
        if (ready()) return;
        throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED,
                "PayOS chưa được cấu hình. Cần client id, api key, checksum key và URL HTTPS.");
    }

    public boolean ready() {
        return enabled && filled(clientId) && filled(apiKey) && filled(checksumKey)
                && payosEndpoint() && webUrl(returnUrl) && webUrl(cancelUrl);
    }

    private boolean payosEndpoint() {
        return endpoint != null && endpoint.startsWith("https://api-merchant.payos.vn/") && !endpoint.contains("?");
    }

    private static boolean webUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://localhost")
                || value.startsWith("http://127.0.0.1"));
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean wholeVnd(BigDecimal amount) {
        if (amount == null) return false;
        try {
            long value = amount.longValueExact();
            return value > 0 && value <= MAX_AMOUNT;
        } catch (ArithmeticException failure) {
            return false;
        }
    }
}

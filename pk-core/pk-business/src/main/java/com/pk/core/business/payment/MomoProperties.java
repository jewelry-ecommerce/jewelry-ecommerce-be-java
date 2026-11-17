package com.pk.core.business.payment;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.momo")
public class MomoProperties {
    public static final String PROVIDER = "MOMO";
    public static final String REQUEST_TYPE = "captureWallet";
    public static final long MAX_AMOUNT = 50_000_000L;

    private boolean enabled = true;
    private String partnerCode = "MOMO";
    private String accessKey = "F8BBA842ECF85";
    private String secretKey = "K951B6PE1waDMi640xX08PD3vg6EkVlz";
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
    private String redirectUrl = "http://localhost:8080/api/v1/storefront/payment/momo/return";
    private String ipnUrl = "http://localhost:8080/api/v1/storefront/payment/momo/ipn";
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
        throw BusinessException.badRequest(ErrorCode.PAYMENT_METHOD_UNSUPPORTED, "Chỉ hỗ trợ COD hoặc MoMo.");
    }

    public void validateAmount(String method, BigDecimal amount) {
        if (!PROVIDER.equals(method) || wholeVnd(amount)) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "MoMo yêu cầu số tiền nguyên đồng lớn hơn 0.");
    }

    public void requireReady() {
        if (ready()) return;
        throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED,
                "MoMo chưa được cấu hình. Cần partner code, access key, secret key và URL HTTPS.");
    }

    public boolean ready() {
        return enabled && filled(partnerCode) && filled(accessKey) && filled(secretKey)
                && momoEndpoint() && webUrl(redirectUrl) && webUrl(ipnUrl);
    }

    private boolean momoEndpoint() {
        return endpoint != null && (endpoint.startsWith("https://test-payment.momo.vn/")
                || endpoint.startsWith("https://payment.momo.vn/")) && !endpoint.contains("?");
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

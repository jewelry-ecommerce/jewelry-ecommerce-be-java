package com.pk.core.business.payment;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnpayProperties {
    public static final String PROVIDER = "VNPAY";
    public static final String SANDBOX_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final String VERSION = "2.1.0";
    public static final String COMMAND = "pay";
    public static final String CURRENCY = "VND";
    public static final String ORDER_TYPE = "other";
    public static final String DEMO_PATH = "/thanh-toan-vnpay/demo";
    public static final long MAX_AMOUNT = 999_999_999L;

    private boolean enabled;
    private boolean demoMode;
    private String tmnCode = "";
    private String hashSecret = "";
    private String payUrl = SANDBOX_PAY_URL;
    private String returnUrl = "";
    private String ipnUrl = "";
    private String clientBaseUrl = "";
    private Duration timeout = Duration.ofMinutes(15);

    public boolean ready() {
        return demoMode ? demoReady() : sandboxReady();
    }

    public void requireReady() {
        if (demoMode) {
            requireDemoReady();
            return;
        }
        requireSandboxReady();
    }

    public String canonical(String method) {
        validateMethod(method);
        return PROVIDER.equalsIgnoreCase(method.trim()) ? PROVIDER : "COD";
    }

    public void validateMethod(String method) {
        if (method != null && "COD".equalsIgnoreCase(method.trim())) return;
        if (method != null && PROVIDER.equalsIgnoreCase(method.trim()) && ready()) return;
        if (method != null && PROVIDER.equalsIgnoreCase(method.trim())) {
            requireReady();
            return;
        }
        throw BusinessException.badRequest(ErrorCode.PAYMENT_METHOD_UNSUPPORTED, "Chỉ hỗ trợ COD hoặc VNPay Sandbox.");
    }

    public void validateAmount(String method, BigDecimal amount) {
        if (!PROVIDER.equals(method) || wholeVnd(amount)) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "VNPay yêu cầu số tiền nguyên đồng lớn hơn 0.");
    }

    private boolean demoReady() {
        return enabled && filled(hashSecret) && webUrl(clientBaseUrl);
    }

    private void requireDemoReady() {
        if (demoReady()) return;
        throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED,
                "Chế độ demo VNPay chưa sẵn sàng. Cần bật thanh toán, hash secret và địa chỉ client.");
    }

    private boolean sandboxReady() {
        return enabled && filled(tmnCode) && filled(hashSecret) && sandboxPayUrl()
                && webUrl(returnUrl) && webUrl(ipnUrl);
    }

    private void requireSandboxReady() {
        if (!enabled || !filled(tmnCode) || !filled(hashSecret) || !webUrl(returnUrl) || !webUrl(ipnUrl)) {
            throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "VNPay Sandbox chưa được cấu hình. Vui lòng chọn COD hoặc bật demo-mode.");
        }
        if (!sandboxPayUrl()) {
            throw new BusinessException(503, ErrorCode.VNPAY_UNAVAILABLE,
                    "Chế độ VNPay thật chỉ mở cổng Sandbox, không thanh toán tiền thật.");
        }
    }

    private boolean sandboxPayUrl() {
        return payUrl != null && payUrl.startsWith("https://sandbox.vnpayment.vn/") && !payUrl.contains("?");
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

package com.pk.core.business.payment;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VnpayGateway {
    private static final ZoneId HANOI = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayProperties properties;

    public VnpayGateway(VnpayProperties properties) {
        this.properties = properties;
    }

    public String buildCheckoutUrl(OrderEntity order, PaymentEntity payment, String clientIp) {
        properties.requireReady();
        if (properties.isDemoMode()) return demoUrl(order, payment);
        Map<String, String> fields = fields(order, payment, clientIp);
        String hash = VnpaySignature.sign(properties.getHashSecret(), fields);
        return requireSandbox(VnpaySignature.paymentUrl(properties.getPayUrl(), fields, hash));
    }

    public static String orderInfo(String code) {
        return "Thanh toan don " + code;
    }

    static String demoPayload(String orderCode, String txnRef, BigDecimal amount) {
        return orderCode + "|" + txnRef + "|" + plainAmount(amount);
    }

    static String plainAmount(BigDecimal amount) {
        try {
            long value = amount.longValueExact();
            if (value <= 0 || value > VnpayProperties.MAX_AMOUNT) throw new ArithmeticException("range");
            return Long.toString(value);
        } catch (ArithmeticException | NullPointerException failure) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Số tiền thanh toán thử không hợp lệ.");
        }
    }

    public static String requireDemo(String value, String clientBaseUrl) {
        try {
            if (demoTarget(URI.create(value), URI.create(clientOrigin(clientBaseUrl)))) return value;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw demoUnavailable();
        }
        throw demoUnavailable();
    }

    public static String requireSandbox(String value) {
        try {
            URI uri = URI.create(value);
            if (sandbox(uri)) return value;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw unavailable();
        }
        throw unavailable();
    }

    private Map<String, String> fields(OrderEntity order, PaymentEntity payment, String clientIp) {
        ZonedDateTime created = ZonedDateTime.now(HANOI);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", VnpayProperties.VERSION);
        fields.put("vnp_Command", VnpayProperties.COMMAND);
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_Amount", String.valueOf(minorAmount(payment)));
        fields.put("vnp_CurrCode", VnpayProperties.CURRENCY);
        fields.put("vnp_TxnRef", payment.getGatewayOrderId());
        fields.put("vnp_OrderInfo", orderInfo(order.getCode()));
        fields.put("vnp_OrderType", VnpayProperties.ORDER_TYPE);
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.getReturnUrl());
        fields.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        fields.put("vnp_CreateDate", created.format(STAMP));
        fields.put("vnp_ExpireDate", created.plus(properties.getTimeout()).format(STAMP));
        return fields;
    }

    private static long minorAmount(PaymentEntity payment) {
        try {
            return payment.getAmount().movePointRight(2).longValueExact();
        } catch (ArithmeticException | NullPointerException failure) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Số tiền VNPay không hợp lệ.");
        }
    }

    private String demoUrl(OrderEntity order, PaymentEntity payment) {
        String payload = demoPayload(order.getCode(), payment.getGatewayOrderId(), payment.getAmount());
        String sig = VnpaySignature.signText(properties.getHashSecret(), payload);
        String amount = plainAmount(payment.getAmount());
        return clientOrigin(properties.getClientBaseUrl()) + VnpayProperties.DEMO_PATH
                + "?orderCode=" + encode(order.getCode())
                + "&txnRef=" + encode(payment.getGatewayOrderId())
                + "&amount=" + encode(amount)
                + "&sig=" + encode(sig);
    }

    private static boolean demoTarget(URI pay, URI base) {
        if (pay.getHost() == null || base.getHost() == null || pay.getScheme() == null || base.getScheme() == null) return false;
        return pay.getScheme().equalsIgnoreCase(base.getScheme())
                && pay.getHost().equalsIgnoreCase(base.getHost())
                && pay.getPort() == base.getPort()
                && pay.getUserInfo() == null
                && VnpayProperties.DEMO_PATH.equals(pay.getPath());
    }

    private static String clientOrigin(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean sandbox(URI uri) {
        return "https".equals(uri.getScheme()) && "sandbox.vnpayment.vn".equals(uri.getHost())
                && uri.getUserInfo() == null && uri.getPort() == -1
                && uri.getPath() != null && uri.getPath().startsWith("/paymentv2/vpcpay.html");
    }

    private static BusinessException unavailable() {
        return new BusinessException(503, ErrorCode.VNPAY_UNAVAILABLE,
                "Chỉ mở trang thanh toán VNPay Sandbox. Đơn đã được giữ; xem lịch sử đơn trước khi đặt lại.");
    }

    private static BusinessException demoUnavailable() {
        return new BusinessException(503, ErrorCode.VNPAY_UNAVAILABLE,
                "Chỉ mở trang thanh toán thử trên client đã cấu hình. Đơn đã được giữ.");
    }
}

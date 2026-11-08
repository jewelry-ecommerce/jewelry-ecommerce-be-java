package com.pk.core.business.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MomoGateway {
    private final MomoProperties properties;
    private final RestClient client;
    public MomoGateway(MomoProperties properties, @Qualifier("momoRestClient") RestClient client) {
        this.properties = properties; this.client = client;
    }
    public String create(OrderEntity order, PaymentEntity payment) {
        properties.requireReady();
        Map<String, Object> body = request(order, payment);
        try {
            Result result = client.post().uri("/v2/gateway/api/create").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Result.class);
            if (result == null || result.resultCode() == null || result.resultCode() != 0
                    || !payment.getGatewayOrderId().equals(result.orderId())
                    || !payment.getGatewayOrderId().equals(result.requestId())) throw unavailable();
            return validatePayUrl(result.payUrl());
        } catch (RestClientException failure) { throw unavailable(); }
    }
    private Map<String, Object> request(OrderEntity order, PaymentEntity payment) {
        long amount;
        try { amount = payment.getAmount().longValueExact(); }
        catch (ArithmeticException failure) { throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Số tiền không hợp lệ."); }
        if (amount < MomoProperties.MIN_AMOUNT || amount > MomoProperties.MAX_AMOUNT) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "MoMo hỗ trợ từ 1.000 đến 50.000.000 đồng.");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("accessKey", properties.getAccessKey()); fields.put("amount", amount); fields.put("extraData", "");
        fields.put("ipnUrl", properties.getIpnUrl()); fields.put("orderId", payment.getGatewayOrderId());
        fields.put("orderInfo", orderInfo(order.getCode())); fields.put("partnerCode", properties.getPartnerCode());
        fields.put("redirectUrl", UriComponentsBuilder.fromUriString(properties.getRedirectUrl())
                .queryParam("orderCode", order.getCode()).build().encode().toUriString());
        fields.put("requestId", payment.getGatewayOrderId()); fields.put("requestType", MomoProperties.REQUEST_TYPE);
        String signature = MomoSignature.sign(properties.getSecretKey(), fields);
        fields.remove("accessKey"); fields.put("signature", signature); fields.put("autoCapture", true); fields.put("lang", "vi");
        return fields;
    }
    public static String orderInfo(String code) { return "Thanh toan don " + code; }
    public static String validatePayUrl(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equals(uri.getScheme()) && "test-payment.momo.vn".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1) return value;
        } catch (IllegalArgumentException | NullPointerException failure) { throw unavailable(); }
        throw unavailable();
    }
    private static BusinessException unavailable() {
        return new BusinessException(503, ErrorCode.MOMO_UNAVAILABLE,
                "Chưa lấy được trang thanh toán MoMo. Đơn đã được giữ; xem lịch sử đơn trước khi đặt lại.");
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Integer resultCode, String orderId, String requestId, String payUrl) {}
}

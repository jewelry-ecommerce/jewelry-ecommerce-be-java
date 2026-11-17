package com.pk.core.business.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tạo link MoMo. Chỉ chấp nhận payUrl thuộc host MoMo. */
@Component
public class MomoGateway {
    private final MomoProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client = RestClient.create();

    public MomoGateway(MomoProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public String create(OrderEntity order, PaymentEntity payment) {
        properties.requireReady();
        try {
            String raw = client.post()
                    .uri(URI.create(properties.getEndpoint()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(order, payment))
                    .retrieve()
                    .body(String.class);
            return payUrl(raw);
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException failure) {
            throw unavailable("Không kết nối được MoMo.");
        }
    }

    private Map<String, Object> body(OrderEntity order, PaymentEntity payment) {
        String orderId = payment.getGatewayOrderId();
        String requestId = orderId + "R";
        String amount = String.valueOf(payment.getAmount().longValueExact());
        String orderInfo = "Thanh toan " + order.getCode();
        String raw = MomoSignature.createRaw(properties.getAccessKey(), amount, "", properties.getIpnUrl(), orderId,
                orderInfo, properties.getPartnerCode(), properties.getRedirectUrl(), requestId, MomoProperties.REQUEST_TYPE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.getPartnerCode());
        body.put("requestId", requestId);
        body.put("amount", Long.valueOf(amount));
        body.put("orderId", orderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", properties.getRedirectUrl());
        body.put("ipnUrl", properties.getIpnUrl());
        body.put("requestType", MomoProperties.REQUEST_TYPE);
        body.put("extraData", "");
        body.put("lang", "vi");
        body.put("signature", MomoSignature.sign(properties.getSecretKey(), raw));
        return body;
    }

    private String payUrl(String raw) {
        try {
            JsonNode node = mapper.readTree(raw);
            String url = node.path("payUrl").asText("");
            if (node.path("resultCode").asInt(-1) == 0 && allowed(url)) return url;
            throw unavailable(node.path("message").asText("MoMo không tạo được link thanh toán."));
        } catch (BusinessException error) {
            throw error;
        } catch (Exception failure) {
            throw unavailable("MoMo trả về không đúng định dạng.");
        }
    }

    public static boolean allowed(String url) {
        return url != null && (url.startsWith("https://test-payment.momo.vn/") || url.startsWith("https://payment.momo.vn/"));
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(503, ErrorCode.MOMO_UNAVAILABLE, message);
    }
}

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

/** Tạo link PayOS. Chỉ chấp nhận checkoutUrl thuộc host pay.payos.vn. */
@Component
public class PayosGateway {
    private final PayosProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client = RestClient.create();

    public PayosGateway(PayosProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public String create(OrderEntity order, PaymentEntity payment) {
        properties.requireReady();
        try {
            String raw = client.post()
                    .uri(URI.create(properties.getEndpoint()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-client-id", properties.getClientId())
                    .header("x-api-key", properties.getApiKey())
                    .body(body(order, payment))
                    .retrieve()
                    .body(String.class);
            return checkoutUrl(raw);
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException failure) {
            throw unavailable("Không kết nối được PayOS.");
        }
    }

    private Map<String, Object> body(OrderEntity order, PaymentEntity payment) {
        long orderCode = Long.parseLong(payment.getGatewayOrderId());
        long amount = payment.getAmount().longValueExact();
        String description = describe(order.getCode());
        String raw = PayosSignature.createRaw(amount, properties.getCancelUrl(), description, orderCode,
                properties.getReturnUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("returnUrl", properties.getReturnUrl());
        body.put("cancelUrl", properties.getCancelUrl());
        body.put("signature", PayosSignature.sign(properties.getChecksumKey(), raw));
        return body;
    }

    private String checkoutUrl(String raw) {
        try {
            JsonNode node = mapper.readTree(raw);
            String url = node.path("data").path("checkoutUrl").asText("").trim();
            if ("00".equals(node.path("code").asText()) && allowed(url)) return url;
            throw unavailable(node.path("desc").asText("PayOS không tạo được link thanh toán."));
        } catch (BusinessException error) {
            throw error;
        } catch (Exception failure) {
            throw unavailable("PayOS trả về không đúng định dạng.");
        }
    }

    public static boolean allowed(String url) {
        return url != null && url.startsWith("https://pay.payos.vn/");
    }

    public static String describe(String orderCode) {
        String digits = orderCode == null ? "" : orderCode.replaceAll("[^A-Za-z0-9]", "");
        if (digits.isBlank()) return "JEWELRY";
        return digits.length() <= PayosProperties.DESCRIPTION_MAX
                ? digits
                : digits.substring(digits.length() - PayosProperties.DESCRIPTION_MAX);
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(503, ErrorCode.PAYOS_UNAVAILABLE, message);
    }
}

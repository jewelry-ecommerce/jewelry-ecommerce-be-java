package com.pk.core.test.business;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.model.entity.CustomerAddressEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vnpay_demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "app.payment.vnpay.enabled=true",
        "app.payment.vnpay.demo-mode=true",
        "app.payment.vnpay.hash-secret=jewelry-vnpay-demo-only",
        "app.payment.vnpay.client-base-url=http://localhost:3100"
})
class VnpayDemoIT extends IntegrationTestBase {
    private static final String BASE = "/api/v1/storefront/payment";

    @Autowired OrderRepo orders;
    @Autowired PaymentRepo payments;

    @Test
    void demoStartConfirmsPaidAndRejectsBadSignature() throws Exception {
        OrderEntity order = order();
        String payUrl = body(postJson(BASE + "/vnpay/start", Map.of("orderCode", order.getCode(), "phone", order.getShipPhone()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redirect").value(true))).get("payUrl").asText();
        assertTrue(payUrl.contains("http://localhost:3100/thanh-toan-vnpay/demo"));
        assertFalse(payUrl.contains("sandbox.vnpayment.vn"));
        Map<String, String> fields = query(payUrl);
        postJson(BASE + "/vnpay/demo/confirm", confirm(fields, fields.get("sig"), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
        assertEquals(OrderEntity.PAID, orders.findOne(order.getId()).getPaymentStatus());
        assertEquals(PaymentEntity.SUCCESS, payments.findByOrderId(order.getId()).getStatus());

        OrderEntity rejected = order();
        Map<String, String> tampered = query(body(postJson(BASE + "/vnpay/start",
                Map.of("orderCode", rejected.getCode(), "phone", rejected.getShipPhone()))).get("payUrl").asText());
        postJson(BASE + "/vnpay/demo/confirm", confirm(tampered, "ab", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PAYMENT_SIGNATURE_INVALID"));
        assertEquals(OrderEntity.UNPAID, orders.findOne(rejected.getId()).getPaymentStatus());
    }

    private OrderEntity order() {
        var address = new CustomerAddressEntity(null, "Guest", uniquePhone(), "HCM", "", "Ward", "12 Street");
        var created = new OrderEntity(null, "VNPAY", "STANDARD", address, new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        orders.create(created);
        PaymentEntity payment = new PaymentEntity(created.getId(), "VNPAY", created.getGrandTotal());
        payments.create(payment);
        payment.setGatewayOrderId(created.getCode() + payment.getId());
        payments.update(payment);
        return created;
    }

    private static Map<String, Object> confirm(Map<String, String> fields, String sig, boolean success) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", fields.get("orderCode"));
        body.put("txnRef", fields.get("txnRef"));
        body.put("amount", new BigDecimal(fields.get("amount")));
        body.put("success", success);
        body.put("sig", sig);
        return body;
    }

    private static Map<String, String> query(String payUrl) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : URI.create(payUrl).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            fields.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return fields;
    }
}

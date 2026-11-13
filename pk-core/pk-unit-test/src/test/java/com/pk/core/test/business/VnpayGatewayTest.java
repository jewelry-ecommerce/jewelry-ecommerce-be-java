package com.pk.core.test.business;

import com.pk.core.business.payment.VnpayGateway;
import com.pk.core.business.payment.VnpayProperties;
import com.pk.core.business.payment.VnpaySignature;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpayGatewayTest {
    @Test
    void buildsSandboxUrlWithAmountTimesOneHundredAndVerifiableHash() {
        VnpayProperties settings = ready();
        OrderEntity order = new OrderEntity();
        order.setCode("DH1");
        PaymentEntity payment = new PaymentEntity(1L, "VNPAY", new BigDecimal("25000"));
        payment.setGatewayOrderId("DH11");
        String payUrl = new VnpayGateway(settings).buildCheckoutUrl(order, payment, "127.0.0.1");
        Map<String, String> fields = query(payUrl);
        assertEquals("2500000", fields.get("vnp_Amount"));
        assertEquals("pay", fields.get("vnp_Command"));
        assertEquals("2.1.0", fields.get("vnp_Version"));
        assertEquals("VND", fields.get("vnp_CurrCode"));
        assertTrue(VnpaySignature.matches(settings.getHashSecret(), fields, fields.remove("vnp_SecureHash")));
        assertThrows(BusinessException.class, () -> VnpayGateway.requireSandbox("https://pay.vnpay.vn/vpcpay.html"));
    }

    @Test
    void demoCheckoutPointsAtLocalDemoPage() {
        VnpayProperties settings = ready();
        settings.setDemoMode(true);
        settings.setClientBaseUrl("http://localhost:3100/");
        settings.setTmnCode("");
        settings.setReturnUrl("");
        settings.setIpnUrl("");
        settings.setPayUrl("http://localhost:3100/not-vnpay");
        OrderEntity order = new OrderEntity();
        order.setCode("DH1");
        PaymentEntity payment = new PaymentEntity(1L, "VNPAY", new BigDecimal("25000"));
        payment.setGatewayOrderId("DH11");
        String payUrl = new VnpayGateway(settings).buildCheckoutUrl(order, payment, "127.0.0.1");
        URI uri = URI.create(payUrl);
        assertEquals("/thanh-toan-vnpay/demo", uri.getPath());
        assertEquals("localhost", uri.getHost());
        assertTrue(payUrl.startsWith("http://localhost:3100/thanh-toan-vnpay/demo?"));
        Map<String, String> fields = query(payUrl);
        assertEquals("DH1", fields.get("orderCode"));
        assertEquals("DH11", fields.get("txnRef"));
        assertEquals("25000", fields.get("amount"));
        assertTrue(VnpaySignature.matchesText(settings.getHashSecret(), "DH1|DH11|25000", fields.get("sig")));
        assertEquals(payUrl, VnpayGateway.requireDemo(payUrl, settings.getClientBaseUrl()));
    }

    @Test
    void demoModeOffStillRequiresSandboxHost() {
        VnpayProperties live = ready();
        live.setDemoMode(false);
        live.setPayUrl("http://localhost:3100/thanh-toan-vnpay/demo");
        BusinessException blocked = assertThrows(BusinessException.class, live::requireReady);
        assertEquals(ErrorCode.VNPAY_UNAVAILABLE, blocked.getCode());
        OrderEntity order = new OrderEntity();
        order.setCode("DH1");
        PaymentEntity payment = new PaymentEntity(1L, "VNPAY", new BigDecimal("25000"));
        payment.setGatewayOrderId("DH11");
        assertThrows(BusinessException.class, () -> new VnpayGateway(live).buildCheckoutUrl(order, payment, "127.0.0.1"));
    }

    @Test
    void codWorksWhileDisabledSandboxRejectsVnpayAndLiveGateway() {
        VnpayProperties disabled = new VnpayProperties();
        disabled.validateMethod("COD");
        BusinessException closed = assertThrows(BusinessException.class, () -> disabled.validateMethod("VNPAY"));
        assertEquals(ErrorCode.PAYMENT_NOT_CONFIGURED, closed.getCode());
        VnpayProperties live = ready();
        live.setPayUrl("https://pay.vnpay.vn/vpcpay.html");
        BusinessException blocked = assertThrows(BusinessException.class, live::requireReady);
        assertEquals(ErrorCode.VNPAY_UNAVAILABLE, blocked.getCode());
    }

    private static VnpayProperties ready() {
        VnpayProperties settings = new VnpayProperties();
        settings.setEnabled(true);
        settings.setDemoMode(false);
        settings.setTmnCode("SANDBOX");
        settings.setHashSecret("sandbox-secret");
        settings.setPayUrl(VnpayProperties.SANDBOX_PAY_URL);
        settings.setReturnUrl("https://client.test/thanh-toan-vnpay");
        settings.setIpnUrl("https://api.test/ipn");
        return settings;
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

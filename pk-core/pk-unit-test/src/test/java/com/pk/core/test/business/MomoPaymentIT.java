package com.pk.core.test.business;

import com.pk.core.business.payment.*;
import com.pk.core.business.repository.*;
import com.pk.core.model.entity.*;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:momo_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
    "app.payment.momo.enabled=true", "app.payment.momo.partner-code=TEST_PARTNER",
    "app.payment.momo.access-key=test-access", "app.payment.momo.secret-key=test-secret-not-real",
    "app.payment.momo.redirect-url=https://client.test/thanh-toan-momo", "app.payment.momo.ipn-url=https://api.test/ipn"})
class MomoPaymentIT extends IntegrationTestBase {
    private static final String BASE = "/api/v1/storefront/payment";
    @Autowired OrderRepo orders;
    @Autowired PaymentRepo payments;
    @Autowired OrderTimelineRepo timelines;
    @Autowired MomoProperties settings;
    @MockBean MomoGateway gateway;

    private OrderEntity order(String method) {
        var address = new CustomerAddressEntity(null, "Guest", uniquePhone(), "HCM", "", "Ward", "12 Street");
        var order = new OrderEntity(null, method, "STANDARD", address, new BigDecimal("25000"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        orders.create(order);
        if ("MOMO".equals(method)) payments.create(new PaymentEntity(order.getId(), method, order.getGrandTotal()));
        return order;
    }
    private Map<String, Object> notification(OrderEntity order, int resultCode, long amount) {
        var payment = payments.findByOrderId(order.getId());
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("accessKey", settings.getAccessKey(), "amount", amount,
            "extraData", "", "message", "Sandbox", "orderId", payment.getGatewayOrderId(),
            "orderInfo", "Thanh toan don " + order.getCode(), "orderType", "momo_wallet",
            "partnerCode", settings.getPartnerCode(), "payType", "qr", "requestId", payment.getGatewayOrderId()));
        fields.put("responseTime", 1790000000000L); fields.put("resultCode", resultCode); fields.put("transId", 12345L);
        String signature = MomoSignature.sign(settings.getSecretKey(), fields);
        fields.remove("accessKey"); fields.put("signature", signature);
        return fields;
    }
    @Test void methodsArePublicAndDisabledConfigurationFailsClosed() throws Exception {
        mvc.perform(get(BASE + "/methods")).andExpect(status().isOk()).andExpect(jsonPath("$.data.momoEnabled").value(true));
        settings.setEnabled(false);
        try {
            postJson(BASE + "/momo/start", Map.of("orderCode", "UNKNOWN")).andExpect(status().isServiceUnavailable());
        } finally { settings.setEnabled(true); }
    }
    @Test void guestStartChecksOwnershipAndReusesOneCheckoutUrl() throws Exception {
        var order = order("MOMO");
        postJson(BASE + "/momo/start", Map.of("orderCode", order.getCode(), "phone", "0000000000"))
            .andExpect(status().isNotFound());
        when(gateway.create(any(), any())).thenReturn("https://test-payment.momo.vn/pay/123");
        var request = Map.of("orderCode", order.getCode(), "phone", order.getShipPhone());
        postJson(BASE + "/momo/start", request).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentUrl").value("https://test-payment.momo.vn/pay/123"));
        postJson(BASE + "/momo/start", request).andExpect(status().isOk());
        verify(gateway, times(1)).create(any(), any());
        assertEquals(OrderEntity.UNPAID, orders.findOne(order.getId()).getPaymentStatus());
    }
    @Test void validSuccessIsIdempotentAndLaterFailureCannotUndoPaid() throws Exception {
        var order = order("MOMO"); var notice = notification(order, 0, 25000);
        postJson(BASE + "/momo/ipn", notice).andExpect(status().isNoContent());
        postJson(BASE + "/momo/ipn", notice).andExpect(status().isNoContent());
        postJson(BASE + "/momo/ipn", notification(order, 1006, 25000)).andExpect(status().isNoContent());
        assertEquals(OrderEntity.PAID, orders.findOne(order.getId()).getPaymentStatus());
        assertEquals(OrderEntity.PENDING, orders.findOne(order.getId()).getStatus());
        assertEquals(1, timelines.findByOrderId(order.getId()).size());
    }
    @Test void tamperedSignatureAndSignedWrongAmountNeverMarkPaid() throws Exception {
        var order = order("MOMO"); var notice = notification(order, 0, 25000);
        notice.put("signature", "0".repeat(64));
        postJson(BASE + "/momo/ipn", notice).andExpect(status().isBadRequest());
        postJson(BASE + "/momo/ipn", notification(order, 0, 26000)).andExpect(status().isBadRequest());
        assertEquals(OrderEntity.UNPAID, orders.findOne(order.getId()).getPaymentStatus());
    }
    @Test void authorizedAndFailedCallbacksDoNotMeanPaid() throws Exception {
        var order = order("MOMO");
        postJson(BASE + "/momo/ipn", notification(order, 9000, 25000)).andExpect(status().isNoContent());
        assertEquals(PaymentEntity.PENDING, payments.findByOrderId(order.getId()).getStatus());
        postJson(BASE + "/momo/ipn", notification(order, 1006, 25000)).andExpect(status().isNoContent());
        assertEquals(PaymentEntity.FAILED, payments.findByOrderId(order.getId()).getStatus());
        assertEquals(OrderEntity.UNPAID, orders.findOne(order.getId()).getPaymentStatus());
    }
    @Test void lateSuccessRecordsMoneyButDoesNotReopenCancelledOrder() throws Exception {
        var order = order("MOMO"); order.cancel(); orders.update(order);
        postJson(BASE + "/momo/ipn", notification(order, 0, 25000)).andExpect(status().isNoContent());
        var updated = orders.findOne(order.getId());
        assertEquals(OrderEntity.CANCELLED, updated.getStatus()); assertEquals(OrderEntity.PAID, updated.getPaymentStatus());
        assertTrue(timelines.findByOrderId(order.getId()).get(0).getNote().contains("hoàn tiền"));
    }
    @Test void codCannotStartMomo() throws Exception {
        var order = order("COD");
        postJson(BASE + "/momo/start", Map.of("orderCode", order.getCode(), "phone", order.getShipPhone()))
            .andExpect(status().isConflict());
    }
}

package com.pk.core.test.business;

import com.pk.core.business.payment.VnpayGateway;
import com.pk.core.business.payment.VnpayProperties;
import com.pk.core.business.payment.VnpaySignature;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.model.entity.CustomerAddressEntity;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vnpay_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "app.payment.vnpay.enabled=true",
        "app.payment.vnpay.demo-mode=false",
        "app.payment.vnpay.tmn-code=SANDBOX",
        "app.payment.vnpay.hash-secret=sandbox-secret",
        "app.payment.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.payment.vnpay.return-url=https://client.test/thanh-toan-vnpay",
        "app.payment.vnpay.ipn-url=https://api.test/ipn"
})
class VnpayPaymentIT extends IntegrationTestBase {
    private static final String BASE = "/api/v1/storefront/payment";

    @Autowired OrderRepo orders;
    @Autowired PaymentRepo payments;
    @Autowired OrderTimelineRepo timelines;
    @Autowired VnpayProperties settings;

    @Test
    void startRejectsWhenSandboxIsDisabled() throws Exception {
        settings.setEnabled(false);
        try {
            postJson(BASE + "/vnpay/start", Map.of("orderCode", "UNKNOWN")).andExpect(status().isServiceUnavailable());
        } finally {
            settings.setEnabled(true);
        }
    }

    @Test
    void guestStartReturnsSandboxPayUrlAndCodCannotStart() throws Exception {
        OrderEntity order = order("VNPAY");
        postJson(BASE + "/vnpay/start", Map.of("orderCode", order.getCode(), "phone", "0000000000"))
                .andExpect(status().isNotFound());
        postJson(BASE + "/vnpay/start", Map.of("orderCode", order.getCode(), "phone", order.getShipPhone()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payUrl").value(org.hamcrest.Matchers.containsString("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html")))
                .andExpect(jsonPath("$.data.redirect").value(true));
        OrderEntity cod = order("COD");
        postJson(BASE + "/vnpay/start", Map.of("orderCode", cod.getCode(), "phone", cod.getShipPhone()))
                .andExpect(status().isConflict());
        assertEquals(OrderEntity.UNPAID, orders.findOne(cod.getId()).getPaymentStatus());
        assertNull(payments.findByOrderId(cod.getId()));
    }

    @Test
    void ipnSuccessMarksPaidAndInvalidHashDoesNot() throws Exception {
        OrderEntity paid = order("VNPAY");
        MultiValueMap<String, String> notice = notice(paid, "00", "00", 2_500_000L);
        mvc.perform(get(BASE + "/vnpay/ipn").params(notice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
        mvc.perform(get(BASE + "/vnpay/ipn").params(notice)).andExpect(jsonPath("$.RspCode").value("00"));
        assertEquals(OrderEntity.PAID, orders.findOne(paid.getId()).getPaymentStatus());
        assertEquals(PaymentEntity.SUCCESS, payments.findByOrderId(paid.getId()).getStatus());
        assertEquals(1, timelines.findByOrderId(paid.getId()).size());
        OrderEntity rejected = order("VNPAY");
        MultiValueMap<String, String> tampered = notice(rejected, "00", "00", 2_500_000L);
        tampered.set("vnp_SecureHash", "ab");
        mvc.perform(get(BASE + "/vnpay/ipn").params(tampered)).andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
        assertEquals(OrderEntity.UNPAID, orders.findOne(rejected.getId()).getPaymentStatus());
    }

    private OrderEntity order(String method) {
        var address = new CustomerAddressEntity(null, "Guest", uniquePhone(), "HCM", "", "Ward", "12 Street");
        var created = new OrderEntity(null, method, "STANDARD", address, new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        orders.create(created);
        if (!"VNPAY".equals(method)) return created;
        PaymentEntity payment = new PaymentEntity(created.getId(), method, created.getGrandTotal());
        payments.create(payment);
        payment.setGatewayOrderId(created.getCode() + payment.getId());
        payments.update(payment);
        return created;
    }

    private MultiValueMap<String, String> notice(OrderEntity order, String response, String txnStatus, long amount) {
        PaymentEntity payment = payments.findByOrderId(order.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Amount", String.valueOf(amount));
        fields.put("vnp_OrderInfo", VnpayGateway.orderInfo(order.getCode()));
        fields.put("vnp_ResponseCode", response);
        fields.put("vnp_TmnCode", settings.getTmnCode());
        fields.put("vnp_TransactionNo", "123456");
        fields.put("vnp_TransactionStatus", txnStatus);
        fields.put("vnp_TxnRef", payment.getGatewayOrderId());
        fields.put("vnp_SecureHash", VnpaySignature.sign(settings.getHashSecret(), fields));
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        fields.forEach(query::add);
        return query;
    }
}

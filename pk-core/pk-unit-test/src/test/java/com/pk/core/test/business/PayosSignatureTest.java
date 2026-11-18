package com.pk.core.test.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.payment.PayosGateway;
import com.pk.core.business.payment.PayosSignature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayosSignatureTest {

    @Test
    void createRawFollowsPayOsFieldOrder() {
        String raw = PayosSignature.createRaw(10000L, "https://shop/cancel", "DH1234567", 42L, "https://shop/return");
        assertEquals("amount=10000&cancelUrl=https://shop/cancel&description=DH1234567&orderCode=42&returnUrl=https://shop/return", raw);
    }

    @Test
    void webhookRawUsesSortedKeysAndEmptyNulls() throws Exception {
        String json = """
                {"amount":3000,"orderCode":123,"description":"VQRIO123","accountNumber":"12345678",
                "reference":"TF1","transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
                "paymentLinkId":"abc","code":"00","desc":"Thành công","counterAccountBankId":null,
                "counterAccountBankName":"","counterAccountName":"","counterAccountNumber":"",
                "virtualAccountName":"","virtualAccountNumber":""}
                """;
        String raw = PayosSignature.webhookRaw(new ObjectMapper().readTree(json));
        assertTrue(raw.startsWith("accountNumber=12345678&amount=3000&code=00&"));
        assertTrue(raw.contains("counterAccountBankId=&"));
        assertTrue(PayosSignature.matches("secret", raw, PayosSignature.sign("secret", raw)));
        assertFalse(PayosSignature.matches("secret", raw, "bad"));
    }

    @Test
    void onlyPayOsCheckoutHostsAreAllowed() {
        assertTrue(PayosGateway.allowed("https://pay.payos.vn/web/abc"));
        assertFalse(PayosGateway.allowed("https://evil.payos.vn/web/abc"));
        assertFalse(PayosGateway.allowed("http://pay.payos.vn/web/abc"));
        assertEquals("370128646", PayosGateway.describe("DH1794913701286466"));
    }
}

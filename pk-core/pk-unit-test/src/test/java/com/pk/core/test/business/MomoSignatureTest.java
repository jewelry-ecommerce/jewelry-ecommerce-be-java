package com.pk.core.test.business;

import com.pk.core.business.payment.MomoGateway;
import com.pk.core.business.payment.MomoSignature;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomoSignatureTest {
    @Test
    void createRawKeepsMomoFieldOrderAndSignature() {
        String raw = MomoSignature.createRaw("access", "25000", "", "https://api.test/ipn", "DH11",
                "Thanh toan DH1", "MOMO", "https://api.test/return", "DH11R", "captureWallet");
        assertEquals("accessKey=access&amount=25000&extraData=&ipnUrl=https://api.test/ipn&orderId=DH11"
                + "&orderInfo=Thanh toan DH1&partnerCode=MOMO&redirectUrl=https://api.test/return"
                + "&requestId=DH11R&requestType=captureWallet", raw);
        String signature = MomoSignature.sign("secret", raw);
        assertTrue(MomoSignature.matches("secret", raw, signature));
        assertFalse(MomoSignature.matches("secret", raw, "ab"));
    }

    @Test
    void noticeRawAndPayUrlHost() {
        String raw = MomoSignature.noticeRaw("access", Map.of(
                "amount", "25000", "extraData", "", "message", "Success", "orderId", "DH11",
                "orderInfo", "Thanh toan DH1", "orderType", "momo_wallet", "partnerCode", "MOMO",
                "payType", "qr", "requestId", "DH11R", "responseTime", "1", "resultCode", "0", "transId", "9"));
        assertTrue(raw.startsWith("accessKey=access&amount=25000&extraData=&message=Success&orderId=DH11"));
        assertTrue(raw.endsWith("&resultCode=0&transId=9"));
        assertTrue(MomoGateway.allowed("https://test-payment.momo.vn/v2/gateway/pay?t=1"));
        assertTrue(MomoGateway.allowed("https://payment.momo.vn/v2/gateway/pay?t=1"));
        assertFalse(MomoGateway.allowed("http://test-payment.momo.vn/pay"));
        assertFalse(MomoGateway.allowed("https://evil.example/pay"));
    }
}

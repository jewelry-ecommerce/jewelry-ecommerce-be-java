package com.pk.core.test.business;

import com.pk.core.business.payment.VnpaySignature;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpaySignatureTest {
    @Test
    void signsSortedEncodedParamsAndRejectsADifferentHash() throws Exception {
        Map<String, String> fields = Map.of("vnp_OrderInfo", "Thanh toan don DH1", "vnp_Amount", "150000");
        String canonical = "vnp_Amount=150000&vnp_OrderInfo=Thanh%20toan%20don%20DH1";
        String hash = VnpaySignature.sign("sandbox-secret", fields);
        assertEquals(hmac(canonical), hash);
        assertTrue(VnpaySignature.matches("sandbox-secret", fields, hash.toUpperCase()));
        assertFalse(VnpaySignature.matches("sandbox-secret", fields, "ab"));
        assertFalse(VnpaySignature.matches("sandbox-secret", Map.of("vnp_SecureHash", hash, "vnp_Amount", ""), hash));
    }

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("sandbox-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}

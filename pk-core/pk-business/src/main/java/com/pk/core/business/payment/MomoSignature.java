package com.pk.core.business.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;

/** HMAC-SHA256 theo đúng thứ tự field MoMo công bố cho create và IPN. */
public final class MomoSignature {
    private static final String HMAC = "HmacSHA256";

    private MomoSignature() {
    }

    public static String createRaw(String accessKey, String amount, String extraData, String ipnUrl, String orderId,
                                   String orderInfo, String partnerCode, String redirectUrl, String requestId,
                                   String requestType) {
        return "accessKey=" + accessKey + "&amount=" + amount + "&extraData=" + extraData + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl + "&requestId=" + requestId + "&requestType=" + requestType;
    }

    public static String noticeRaw(String accessKey, Map<String, String> fields) {
        return "accessKey=" + accessKey + "&amount=" + value(fields, "amount") + "&extraData=" + value(fields, "extraData")
                + "&message=" + value(fields, "message") + "&orderId=" + value(fields, "orderId")
                + "&orderInfo=" + value(fields, "orderInfo") + "&orderType=" + value(fields, "orderType")
                + "&partnerCode=" + value(fields, "partnerCode") + "&payType=" + value(fields, "payType")
                + "&requestId=" + value(fields, "requestId") + "&responseTime=" + value(fields, "responseTime")
                + "&resultCode=" + value(fields, "resultCode") + "&transId=" + value(fields, "transId");
    }

    public static String sign(String secret, String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Không tạo được chữ ký MoMo", failure);
        }
    }

    public static boolean matches(String secret, String raw, String signature) {
        return signature != null && !signature.isBlank() && sign(secret, raw).equalsIgnoreCase(signature);
    }

    private static String value(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return value == null ? "" : value;
    }
}

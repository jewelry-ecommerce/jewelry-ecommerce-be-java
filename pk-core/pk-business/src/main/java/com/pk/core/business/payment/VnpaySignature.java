package com.pk.core.business.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** VNPay 2.1.0: HMAC-SHA512 over sorted params, URL-encoded values, spaces as %20. */
public final class VnpaySignature {
    private VnpaySignature() {
    }

    public static String sign(String secret, Map<String, ?> fields) {
        return hmac(secret, canonical(fields));
    }

    public static boolean matches(String secret, Map<String, ?> fields, String signature) {
        return matchesText(secret, canonical(fields), signature);
    }

    public static String signText(String secret, String payload) {
        return hmac(secret, payload == null ? "" : payload);
    }

    public static boolean matchesText(String secret, String payload, String signature) {
        if (signature == null || signature.isBlank()) return false;
        byte[] expected = signText(secret, payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public static String paymentUrl(String payUrl, Map<String, ?> fields, String hash) {
        StringBuilder query = new StringBuilder();
        new TreeMap<String, String>(text(fields)).forEach((key, value) -> append(query, encode(key), encode(value)));
        query.append("&vnp_SecureHash=").append(hash);
        return payUrl + "?" + query;
    }

    static String canonical(Map<String, ?> fields) {
        StringBuilder hashData = new StringBuilder();
        new TreeMap<String, String>(text(fields)).forEach((key, value) -> append(hashData, key, encode(value)));
        return hashData.toString();
    }

    private static Map<String, String> text(Map<String, ?> fields) {
        Map<String, String> sorted = new TreeMap<>();
        fields.forEach((key, value) -> keep(sorted, key, value));
        return sorted;
    }

    private static void keep(Map<String, String> sorted, String key, Object value) {
        if (key == null || "vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) return;
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty()) sorted.put(key, text);
    }

    private static void append(StringBuilder builder, String key, String value) {
        if (!builder.isEmpty()) builder.append('&');
        builder.append(key).append('=').append(value);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Cannot initialize VNPay signature", failure);
        }
    }
}

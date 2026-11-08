package com.pk.core.business.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** MoMo v2 canonical fields sorted alphabetically, with raw values (not URL encoded). */
public final class MomoSignature {
    private MomoSignature() {}
    public static String sign(String secret, Map<String, ?> fields) {
        String canonical = new TreeMap<>(fields).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Cannot initialize MoMo signature", failure);
        }
    }
    public static boolean matches(String secret, Map<String, ?> fields, String signature) {
        return signature != null && MessageDigest.isEqual(sign(secret, fields).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}

package com.pk.core.business.payment;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** HMAC-SHA256 theo đúng thứ tự alphabet của PayOS. */
public final class PayosSignature {
    private static final String HMAC = "HmacSHA256";

    private PayosSignature() {
    }

    public static String createRaw(long amount, String cancelUrl, String description, long orderCode, String returnUrl) {
        return "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
    }

    public static String webhookRaw(JsonNode data) {
        if (data == null || !data.isObject()) return "";
        Map<String, String> fields = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fieldsIt = data.fields();
        while (fieldsIt.hasNext()) {
            Map.Entry<String, JsonNode> entry = fieldsIt.next();
            fields.put(entry.getKey(), fieldValue(entry.getValue()));
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("&", parts);
    }

    public static String sign(String secret, String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Không tạo được chữ ký PayOS", failure);
        }
    }

    public static boolean matches(String secret, String raw, String signature) {
        return signature != null && !signature.isBlank() && sign(secret, raw).equalsIgnoreCase(signature);
    }

    private static String fieldValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return "";
        if (value.isTextual()) {
            String text = value.asText();
            return ("null".equals(text) || "undefined".equals(text)) ? "" : text;
        }
        if (value.isNumber() || value.isBoolean()) return value.asText();
        return value.toString();
    }
}

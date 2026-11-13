package com.pk.core.business.payment;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.TreeMap;

public final class VnpayQuery {
    private VnpayQuery() {
    }

    public static Map<String, String> from(HttpServletRequest request) {
        Map<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((key, values) -> keep(params, key, values));
        return params;
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded == null ? "" : forwarded.split(",")[0].trim();
        if (!isIp(candidate)) candidate = request.getRemoteAddr();
        if (!isIp(candidate) || "::1".equals(candidate) || "0:0:0:0:0:0:0:1".equals(candidate)) return "127.0.0.1";
        return candidate;
    }

    private static void keep(Map<String, String> params, String key, String[] values) {
        if (values == null || values.length == 0 || values[0] == null || values[0].isEmpty()) return;
        params.put(key, values[0]);
    }

    private static boolean isIp(String value) {
        return value != null && value.matches("[0-9a-fA-F:.]{3,45}");
    }
}

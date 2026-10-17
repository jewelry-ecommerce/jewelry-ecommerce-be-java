package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiện ích JSON/ngày/số dùng chung cho nhóm chiến dịch khuyến mãi (wizard) - nội bộ gói service.impl. */
final class PromoJson {

    static final String INVALID = "PROMOTION_INVALID";
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PromoJson() {
    }

    static BusinessException invalid(String message) {
        return BusinessException.badRequest(INVALID, message);
    }

    static BusinessException invalid(String message, String field) {
        return BusinessException.badRequest(INVALID, message, field);
    }

    static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            throw invalid("JSON không hợp lệ");
        }
    }

    static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                if (x != null && !String.valueOf(x).isBlank()) {
                    out.add(String.valueOf(x).trim());
                }
            }
        }
        return out;
    }

    static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    /** Số tiền/giá trị: chấp nhận number hoặc chuỗi số; null nếu rỗng; 400 nếu không phải số. */
    static BigDecimal num(Object o, String field) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException ex) {
            throw invalid("Giá trị số không hợp lệ", field);
        }
    }

    static String iso(Date d) {
        return d == null ? null : d.toInstant().toString();
    }

    static Date date(Object o, String field) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
            return d;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Date.from(OffsetDateTime.parse(s).toInstant());
        } catch (Exception ignored) {
            // thử dạng khác
        }
        try {
            return Date.from(Instant.parse(s));
        } catch (Exception ignored) {
            // thử dạng khác
        }
        try {
            return Date.from(LocalDateTime.parse(s).atZone(ZONE).toInstant());
        } catch (Exception ignored) {
            // thử dạng khác
        }
        try {
            return Date.from(LocalDate.parse(s).atStartOfDay(ZONE).toInstant());
        } catch (Exception ex) {
            throw invalid("Thời gian không hợp lệ (cần dạng ISO-8601)", field);
        }
    }

    static Long id(String raw) {
        try {
            return Long.valueOf(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    static String money(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}

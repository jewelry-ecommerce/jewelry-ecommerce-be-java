package com.pk.core.business.service.impl;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.AdminStockAdjustmentRequestDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Hàm phụ cho sổ kho: chuẩn hoá Idempotency-Key, băm nội dung yêu cầu, đọc khoảng thời gian lọc lịch sử. */
final class InventoryMovementSupport {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    /** Mốc "không giới hạn" của bộ lọc thời gian. */
    private static final Date MIN_DATE = new Date(0L);
    private static final Date MAX_DATE = Date.from(Instant.parse("2999-12-31T00:00:00Z"));

    private InventoryMovementSupport() {
    }

    /** Khoá trống -> null (không chống trùng); sai định dạng -> 400 IDEMPOTENCY_KEY_INVALID. */
    static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        if (!KEY_PATTERN.matcher(k).matches()) {
            throw BusinessException.badRequest(ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key phải dài 8-100 ký tự (chữ, số, - _ . :)");
        }
        return k;
    }

    /** SHA-256 của các trường quyết định kết quả (không gồm warehouseId vì luôn là kho MAIN). */
    static String requestHash(AdminStockAdjustmentRequestDto req) {
        String canonical = req.getSkuId() + "|" + req.getQuantityDelta() + "|" + req.getReason() + "|"
                + nz(req.getReference()) + "|" + nz(req.getNote());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không băm được nội dung điều chỉnh kho", e);
        }
    }

    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nz(String value) {
        String c = clean(value);
        return c == null ? "" : c;
    }

    /** [from, to): null/rỗng = không giới hạn; ngày thuần `to` được tính trọn ngày. Sai định dạng -> 400. */
    static Date[] range(String from, String to) {
        Date f = parse(from, "from", false);
        Date t = parse(to, "to", true);
        return new Date[] {f == null ? MIN_DATE : f, t == null ? MAX_DATE : t};
    }

    private static Date parse(String raw, String field, boolean endOfDayIfDate) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        ZoneId zone = ZoneId.systemDefault();
        try {
            if (v.length() == 10) {
                LocalDate day = LocalDate.parse(v);
                return Date.from((endOfDayIfDate ? day.plusDays(1) : day).atStartOfDay(zone).toInstant());
            }
            try {
                return Date.from(OffsetDateTime.parse(v).toInstant());
            } catch (DateTimeParseException ignored) {
                return Date.from(LocalDateTime.parse(v).atZone(zone).toInstant());
            }
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    field + " không hợp lệ (cần ISO-8601, vd 2026-10-28 hoặc 2026-10-28T08:00:00): " + raw)
                    .withField(field);
        }
    }
}

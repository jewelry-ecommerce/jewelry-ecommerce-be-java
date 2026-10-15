package com.pk.core.business.service.impl;

import com.pk.core.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Quy tắc hợp lệ DÙNG CHUNG cho khuyến mãi theo sản phẩm và voucher (khớp bảng promotions/vouchers và validate phía FE):
 * kiểm tra ở Java để trả 400 DISCOUNT_INVALID có thông điệp rõ, thay vì để DB ném lỗi ràng buộc (ck_*_type, ck_*_period...).
 */
final class DiscountRules {

    static final String INVALID = "DISCOUNT_INVALID";
    static final int MAX_PRODUCTS_PER_PROMOTION = 100;
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED");
    private static final Pattern VOUCHER_CODE = Pattern.compile("[A-Z0-9_-]{3,40}");

    private DiscountRules() {
    }

    static BusinessException invalid(String message, String field) {
        return BusinessException.badRequest(INVALID, message, field);
    }

    static String type(String raw, Set<String> allowed, String what) {
        String t = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!allowed.contains(t)) {
            throw invalid("Loại giảm giá của " + what + " chỉ nhận: " + String.join(", ", new java.util.TreeSet<>(allowed)), "discountType");
        }
        return t;
    }

    static String status(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!STATUSES.contains(s)) {
            throw invalid("status chỉ nhận DRAFT hoặc PUBLISHED", "status");
        }
        return s;
    }

    static void value(String type, BigDecimal value, BigDecimal max) {
        if (value == null || value.signum() < 0) {
            throw invalid("Giá trị giảm không được âm", "discountValue");
        }
        if ("PERCENT".equals(type) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw invalid("Tỷ lệ giảm không được vượt quá 100%", "discountValue");
        }
        if (max != null && max.signum() < 0) {
            throw invalid("Mức giảm tối đa không được âm", "maxDiscountAmount");
        }
    }

    static void period(Date starts, Date ends) {
        if (starts == null || ends == null || !ends.after(starts)) {
            throw invalid("Thời gian kết thúc phải sau thời gian bắt đầu", "endsAt");
        }
    }

    static String voucherCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!VOUCHER_CODE.matcher(code).matches()) {
            throw invalid("Mã voucher gồm 3-40 ký tự chữ, số, gạch ngang hoặc gạch dưới", "code");
        }
        return code;
    }
}

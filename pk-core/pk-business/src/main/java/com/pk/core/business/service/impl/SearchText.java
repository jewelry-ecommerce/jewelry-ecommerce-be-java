package com.pk.core.business.service.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chuẩn hoá từ khoá tìm kiếm: gọn khoảng trắng, chữ thường, giới hạn độ dài, bỏ dấu để so khớp. */
public final class SearchText {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern HAS_LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    private SearchText() {
    }

    /** Dạng hiển thị đã gọn; null nếu quá ngắn/quá dài hoặc không có chữ/số nào (không ghi nhận). */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String v = SPACES.matcher(CONTROL.matcher(raw).replaceAll(" ")).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        if (v.length() < MIN_LENGTH || v.length() > MAX_LENGTH || !HAS_LETTER_OR_DIGIT.matcher(v).find()) {
            return null;
        }
        return v;
    }

    /** Khoá so khớp: bỏ dấu tiếng Việt (đ -> d), chữ thường. */
    public static String key(String cleaned) {
        String d = MARKS.matcher(Normalizer.normalize(cleaned, Normalizer.Form.NFD)).replaceAll("");
        return d.replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT);
    }

    /** Có dấu (khác dạng bỏ dấu) - ưu tiên làm dạng hiển thị. */
    public static boolean hasDiacritics(String cleaned) {
        return !key(cleaned).equals(cleaned);
    }

    /** Escape %, _ và ! để dùng trong LIKE ... ESCAPE '!'. */
    public static String likeEscape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}

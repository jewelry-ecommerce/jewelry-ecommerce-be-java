package com.pk.core.business.service.impl;

import java.util.ArrayList;
import java.util.List;

/** CSV tối giản theo RFC 4180: dấu phẩy, ngoặc kép, xuống dòng trong ô; bỏ BOM UTF-8 khi đọc. */
public final class CsvSupport {

    private CsvSupport() {
    }

    /** Ô bắt đầu bằng = + - @ được thêm dấu ' phía trước để Excel không coi là công thức (CSV injection). */
    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !isNumber(s)) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static boolean isNumber(String s) {
        try {
            new java.math.BigDecimal(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static String line(List<?> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(values.get(i)));
        }
        return sb.toString();
    }

    /** Tách toàn bộ văn bản thành các dòng (bỏ dòng hoàn toàn trống). */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cur.toString());
                cur.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                cur.append(c);
            }
        }
        row.add(cur.toString());
        addRow(rows, row);
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean empty = row.stream().allMatch(s -> s.trim().isEmpty());
        if (!empty) {
            rows.add(row);
        }
    }
}

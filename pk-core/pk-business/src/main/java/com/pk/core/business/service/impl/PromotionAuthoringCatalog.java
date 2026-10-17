package com.pk.core.business.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh mục cấu hình điều kiện/quyền lợi cho wizard khuyến mãi (hợp đồng "authoring V2" của FE): mỗi lựa chọn là
 * một JSON-schema phẳng mà form dựng từ đó; khi lưu, BE chuyển giá trị form thành `config` chuẩn hoá. Chỉ liệt kê
 * những gì engine hiện có hỗ trợ (MARKDOWN % / số tiền, FLAT_PRICE, VOUCHER % / số tiền, điều kiện ALWAYS và
 * tổng tiền tối thiểu) nên FE không có lựa chọn nào mà BE không thực thi được.
 */
final class PromotionAuthoringCatalog {

    static final String VERSION = "1.0.0";
    static final String CONTRACT = "2.0.0";

    private PromotionAuthoringCatalog() {
    }

    static List<Map<String, Object>> conditionOptions(String schemaType) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> always = option("CONDITION", "ALWAYS", "Luôn áp dụng", "Không có điều kiện bổ sung.",
                object("Luôn áp dụng", new LinkedHashMap<>(), List.of()));
        always.put("supportsImplicitAlways", true);
        out.add(always);
        if ("VOUCHER".equals(schemaType)) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("operator", enumString("Điều kiện so sánh", List.of("GREATER_THAN_OR_EQUAL"), List.of("Lớn hơn hoặc bằng"), "GREATER_THAN_OR_EQUAL"));
            props.put("amountMinor", money("Giá trị đơn tối thiểu (đ)", 1));
            out.add(option("CONDITION", "TARGET_SUBTOTAL", "Giá trị đơn tối thiểu", "Voucher chỉ áp dụng khi tổng tiền sản phẩm áp dụng đạt mức tối thiểu.",
                    object("Giá trị đơn tối thiểu", props, List.of("operator", "amountMinor"))));
        }
        return out;
    }

    static List<Map<String, Object>> benefitOptions(String schemaType) {
        Map<String, Object> props = new LinkedHashMap<>();
        switch (schemaType) {
            case "MARKDOWN" -> {
                props.put("discountKind", enumString("Hình thức giảm", List.of("PERCENT", "FIXED_AMOUNT"), List.of("Giảm theo %", "Giảm số tiền"), "PERCENT"));
                props.put("discountValue", number("Giá trị giảm", 0, null, null));
                props.put("maxDiscountAmountMinor", money("Giảm tối đa mỗi sản phẩm (đ) - chỉ khi giảm theo %", 0));
                return List.of(option("BENEFIT", "MARKDOWN", "Giảm giá sản phẩm", "Giảm giá trực tiếp trên giá bán của sản phẩm áp dụng.",
                        object("Giảm giá sản phẩm", props, List.of("discountKind", "discountValue"))));
            }
            case "FLAT_PRICE" -> {
                props.put("fixedPriceAmountMinor", money("Giá đồng giá (đ)", 0));
                return List.of(option("BENEFIT", "FLAT_PRICE", "Đồng giá", "Bán đúng một mức giá cho các sản phẩm áp dụng.",
                        object("Đồng giá", props, List.of("fixedPriceAmountMinor"))));
            }
            default -> {
                props.put("discountKind", enumString("Hình thức giảm", List.of("PERCENT", "FIXED_AMOUNT"), List.of("Giảm theo %", "Giảm số tiền"), "PERCENT"));
                props.put("discountValue", number("Giá trị giảm", 0, null, null));
                props.put("maxDiscountAmountMinor", money("Giảm tối đa (đ) - chỉ khi giảm theo %", 0));
                return List.of(option("BENEFIT", "VOUCHER", "Voucher giảm giá đơn hàng", "Giảm giá trên giá trị đơn hàng khi khách nhập mã.",
                        object("Voucher giảm giá đơn hàng", props, List.of("discountKind", "discountValue"))));
            }
        }
    }

    /** Kiểm tra + chuyển giá trị form của điều kiện thành config chuẩn hoá. */
    static Map<String, Object> conditionConfig(String schemaType, String configType, String version, Map<String, Object> values) {
        checkVersion(version);
        Map<String, Object> cfg = new LinkedHashMap<>();
        if ("ALWAYS".equals(configType)) {
            cfg.put("type", "ALWAYS");
            cfg.put("version", VERSION);
            return cfg;
        }
        if ("TARGET_SUBTOTAL".equals(configType) && "VOUCHER".equals(schemaType)) {
            BigDecimal amount = wholeMoney(values.get("amountMinor"), "amountMinor", true);
            if (amount.signum() <= 0) {
                throw PromoJson.invalid("Giá trị đơn tối thiểu phải lớn hơn 0", "amountMinor");
            }
            String op = PromoJson.str(values.get("operator"));
            if (op != null && !op.isEmpty() && !"GREATER_THAN_OR_EQUAL".equals(op)) {
                throw PromoJson.invalid("Chỉ hỗ trợ điều kiện lớn hơn hoặc bằng", "operator");
            }
            cfg.put("type", "TARGET_SUBTOTAL");
            cfg.put("version", VERSION);
            cfg.put("operator", "GREATER_THAN_OR_EQUAL");
            cfg.put("amountMinor", PromoJson.money(amount));
            cfg.put("subtotalBase", "CURRENT_SELLING_PRICE");
            return cfg;
        }
        throw PromoJson.invalid("Loại điều kiện không được hỗ trợ cho hình thức này: " + configType, "configType");
    }

    static Map<String, Object> benefitConfig(String schemaType, String configType, String version, Map<String, Object> values) {
        checkVersion(version);
        if (!schemaType.equals(configType)) {
            throw PromoJson.invalid("Loại quyền lợi không khớp hình thức khuyến mãi: " + configType, "configType");
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", configType);
        cfg.put("version", VERSION);
        if ("FLAT_PRICE".equals(configType)) {
            BigDecimal price = wholeMoney(values.get("fixedPriceAmountMinor"), "fixedPriceAmountMinor", true);
            if (price.signum() < 0) {
                throw PromoJson.invalid("Giá đồng giá không được âm", "fixedPriceAmountMinor");
            }
            cfg.put("fixedPriceAmountMinor", price.longValueExact());
            cfg.put("applicationScope", "SKU_ITEM");
            return cfg;
        }
        String kind = PromoJson.str(values.get("discountKind"));
        if (!"PERCENT".equals(kind) && !"FIXED_AMOUNT".equals(kind)) {
            throw PromoJson.invalid("Hình thức giảm chỉ nhận PERCENT hoặc FIXED_AMOUNT", "discountKind");
        }
        BigDecimal value = PromoJson.num(values.get("discountValue"), "discountValue");
        if (value == null || value.signum() <= 0) {
            throw PromoJson.invalid("Giá trị giảm phải lớn hơn 0", "discountValue");
        }
        if ("PERCENT".equals(kind) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw PromoJson.invalid("Tỷ lệ giảm không được vượt quá 100%", "discountValue");
        }
        if ("FIXED_AMOUNT".equals(kind)) {
            value = wholeMoney(value, "discountValue", true);
        }
        cfg.put("discountKind", kind);
        cfg.put("discountValue", value.stripTrailingZeros().scale() <= 0 ? (Object) value.longValue() : (Object) value.doubleValue());
        BigDecimal max = wholeMoney(values.get("maxDiscountAmountMinor"), "maxDiscountAmountMinor", false);
        if (max != null && max.signum() > 0 && "PERCENT".equals(kind)) {
            cfg.put("maxDiscountAmount", Map.of("amountMinor", PromoJson.money(max)));
        }
        cfg.put("roundingMode", "HALF_UP");
        if ("VOUCHER".equals(configType)) {
            cfg.put("discountTarget", "ORDER_VALUE");
            cfg.put("discountBase", "AFTER_SKU_ITEM_PROMOTIONS");
            cfg.put("applicationScope", "CART");
            cfg.put("allocationTarget", "TARGET_ELIGIBLE_LINES");
            cfg.put("allocationMethod", "PROPORTIONAL_BY_ELIGIBLE_SUBTOTAL");
        } else {
            cfg.put("discountBase", "CURRENT_SELLING_PRICE");
            cfg.put("applicationScope", "SKU_ITEM");
            cfg.put("allocationTarget", "NONE");
            cfg.put("allocationMethod", "NONE");
        }
        return cfg;
    }

    /** Giá trị form khôi phục lại từ config đã lưu (khi FE mở lại drawer). */
    static Map<String, Object> authoringValues(Map<String, Object> cfg) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (String k : List.of("operator", "amountMinor", "discountKind", "discountValue", "fixedPriceAmountMinor")) {
            if (cfg.containsKey(k)) {
                Object x = cfg.get(k);
                v.put(k, "amountMinor".equals(k) ? Double.valueOf(PromoJson.num(x, k).doubleValue()).longValue() : x);
            }
        }
        Map<String, Object> max = PromoJson.asMap(cfg.get("maxDiscountAmount"));
        if (max != null && max.get("amountMinor") != null) {
            v.put("maxDiscountAmountMinor", PromoJson.num(max.get("amountMinor"), "maxDiscountAmount").longValue());
        }
        return v;
    }

    // ------------------------------------------------------------------ voucher code (collection) schemas

    static Map<String, Object> voucherCollectionSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("imageUrl", string("Ảnh voucher", null));
        for (String k : List.of("issuanceStartsAt", "issuanceEndsAt", "usageStartsAt", "usageEndsAt")) {
            Map<String, Object> d = string(switch (k) {
                case "issuanceStartsAt" -> "Bắt đầu phát hành";
                case "issuanceEndsAt" -> "Kết thúc phát hành";
                case "usageStartsAt" -> "Bắt đầu sử dụng";
                default -> "Kết thúc sử dụng";
            }, null);
            d.put("format", "date-time");
            props.put(k, d);
        }
        return object("Thông tin chung của mã voucher", props, List.of());
    }

    static Map<String, Object> voucherItemSchema() {
        Map<String, Object> code = string("Mã voucher", null);
        code.put("minLength", 3);
        code.put("maxLength", 40);
        code.put("pattern", "^[A-Z0-9_-]+$");
        Map<String, Object> policyProps = new LinkedHashMap<>();
        Map<String, Object> type = string("Loại chính sách", "VOUCHER_VISIBILITY_DISTRIBUTION_POLICY");
        type.put("readOnly", true);
        Map<String, Object> ver = string("Phiên bản", "1.0.0");
        ver.put("readOnly", true);
        policyProps.put("type", type);
        policyProps.put("version", ver);
        policyProps.put("visibilityType", enumString("Hiển thị", List.of("PUBLIC", "PRIVATE"), List.of("Công khai", "Riêng tư"), "PUBLIC"));
        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("type", "array");
        channels.put("title", "Kênh phân phối");
        channels.put("items", enumString("Kênh", List.of("ZNS", "EMAIL", "OTHER"), List.of("Zalo ZNS", "Email", "Khác"), null));
        channels.put("uniqueItems", true);
        channels.put("default", List.of());
        policyProps.put("distributionChannels", channels);
        Map<String, Object> policy = object("Chính sách hiển thị và phân phối", policyProps, List.of("type", "version", "visibilityType", "distributionChannels"));
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("if", Map.of("properties", Map.of("visibilityType", Map.of("const", "PRIVATE"))));
        rule.put("then", Map.of("properties", Map.of("distributionChannels", Map.of("minItems", 1))));
        policy.put("allOf", List.of(rule));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("code", code);
        props.put("status", enumString("Trạng thái", List.of("ACTIVE", "INACTIVE"), List.of("Đang kích hoạt", "Vô hiệu"), "ACTIVE"));
        props.put("visibilityDistributionPolicy", policy);
        return object("Mã voucher", props, List.of("code", "status", "visibilityDistributionPolicy"));
    }

    static Map<String, Object> voucherDefaultItem() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("code", "");
        v.put("status", "ACTIVE");
        v.put("visibilityDistributionPolicy", defaultPolicy());
        return v;
    }

    static Map<String, Object> defaultPolicy() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "VOUCHER_VISIBILITY_DISTRIBUTION_POLICY");
        p.put("version", "1.0.0");
        p.put("visibilityType", "PUBLIC");
        p.put("distributionChannels", List.of());
        return p;
    }

    // ------------------------------------------------------------------ mô tả hiển thị

    static String vnd(BigDecimal v) {
        return String.format(java.util.Locale.ROOT, "%,d", v == null ? 0L : v.longValue()).replace(',', '.') + " đ";
    }

    /** Tóm tắt quyền lợi cho danh sách chiến dịch và thẻ voucher. */
    static String benefitSummary(Map<String, Object> cfg, String schemaType) {
        if (cfg == null || cfg.isEmpty()) {
            return "Chưa cấu hình";
        }
        if ("FLAT_PRICE".equals(cfg.get("type"))) {
            return "Đồng giá " + vnd(PromoJson.num(cfg.get("fixedPriceAmountMinor"), "x"));
        }
        BigDecimal v = PromoJson.num(cfg.get("discountValue"), "x");
        String base = "PERCENT".equals(cfg.get("discountKind")) ? "Giảm " + PromoJson.money(v) + "%" : "Giảm " + vnd(v);
        Map<String, Object> max = PromoJson.asMap(cfg.get("maxDiscountAmount"));
        if (max != null) {
            base += ", tối đa " + vnd(PromoJson.num(max.get("amountMinor"), "x"));
        }
        return "VOUCHER".equals(schemaType) ? base + " đơn hàng" : base;
    }

    static String conditionSummary(Map<String, Object> cfg) {
        if (cfg != null && "TARGET_SUBTOTAL".equals(cfg.get("type"))) {
            return "Đơn từ " + vnd(PromoJson.num(cfg.get("amountMinor"), "x"));
        }
        return "Áp dụng cho mọi đơn hàng";
    }

    // ------------------------------------------------------------------ builders

    private static void checkVersion(String version) {
        if (version != null && !version.isBlank() && !VERSION.equals(version)) {
            throw PromoJson.invalid("Phiên bản cấu hình không được hỗ trợ: " + version, "configVersion");
        }
    }

    /** Số nguyên đồng (VND không có đơn vị lẻ). required=false và rỗng -> null. */
    private static BigDecimal wholeMoney(Object raw, String field, boolean required) {
        BigDecimal v = raw instanceof BigDecimal b ? b : PromoJson.num(raw, field);
        if (v == null) {
            if (required) {
                throw PromoJson.invalid("Giá trị bắt buộc", field);
            }
            return null;
        }
        if (v.stripTrailingZeros().scale() > 0) {
            throw PromoJson.invalid("Số tiền phải là số nguyên (đồng)", field);
        }
        return v;
    }

    private static Map<String, Object> option(String kind, String type, String title, String desc, Map<String, Object> schema) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("authoringContractVersion", CONTRACT);
        o.put("editorType", "SINGLE_OBJECT");
        o.put("componentKind", kind);
        o.put("configType", type);
        o.put("configVersion", VERSION);
        o.put("title", title);
        o.put("description", desc);
        o.put("configSchema", schema);
        return o;
    }

    static Map<String, Object> object(String title, Map<String, Object> props, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("title", title);
        s.put("properties", props);
        s.put("required", required);
        return s;
    }

    private static Map<String, Object> enumString(String title, List<String> values, List<String> names, String def) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "string");
        s.put("title", title);
        s.put("enum", values);
        s.put("enumNames", names);
        if (def != null) {
            s.put("default", def);
        }
        return s;
    }

    private static Map<String, Object> string(String title, String def) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "string");
        s.put("title", title);
        if (def != null) {
            s.put("default", def);
        }
        return s;
    }

    private static Map<String, Object> number(String title, Number min, Number max, Number def) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "number");
        s.put("title", title);
        if (min != null) {
            s.put("minimum", min);
        }
        if (max != null) {
            s.put("maximum", max);
        }
        if (def != null) {
            s.put("default", def);
        }
        return s;
    }

    private static Map<String, Object> money(String title, Number min) {
        Map<String, Object> s = number(title, min, null, null);
        s.put("format", "currency");
        return s;
    }
}

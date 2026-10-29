package com.pk.core.business.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;

/**
 * Kiểm tra cấu trúc `config` của các block CMS do FE gửi (INFO_CARDS, IMAGE_GALLERY, PRODUCT_LIST,
 * PRODUCT_CATEGORY_NAV, PRODUCT_COLLECTION_SHOWCASE, PRODUCT_EXPANDABLE_DESCRIPTION) theo đúng schema FE
 * (jewelry-ecommerce-admin/.../cms/source-schema). Chỉ kiểm field FE đã khai báo; field lạ được GIỮ NGUYÊN
 * (không từ chối, không xoá). Giá trị null của field tuỳ chọn coi như không gửi. Lỗi trả 400
 * {@link ErrorCode#CMS_BLOCK_CONFIG_INVALID} kèm đường dẫn field (vd `config.items[0].imageUrl`).
 * BANNER và PRODUCT_CAROUSEL do CmsServiceImpl tự kiểm.
 */
public final class CmsBlockConfigValidator {

    private static final Set<Integer> INFO_COLUMNS = Set.of(2, 3, 4);
    private static final Set<String> GALLERY_LAYOUTS = Set.of("GRID", "CAROUSEL");
    private static final Set<String> GALLERY_ACTIONS = Set.of("LINK_URL", "MIX_MATCH");
    private static final Set<String> LIST_SOURCES = Set.of("ALL_PRODUCTS", "COLLECTION", "PROMOTION");
    private static final Set<String> LIST_SORTS = Set.of("best_selling", "price_asc", "price_desc", "newest", "name",
            "name_desc");
    private static final Set<String> LIST_BANNER_SOURCES = Set.of("CMS_STOREFRONT");
    private static final int SHOWCASE_MAX_ITEMS = 3;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    private static final Pattern BAD_URL_CHARS = Pattern.compile("[\\s\\p{Cntrl}<>\"'\\\\]");

    private CmsBlockConfigValidator() {
    }

    /** Kiểm config theo loại block; loại không thuộc lớp này (BANNER, PRODUCT_CAROUSEL...) được bỏ qua. */
    public static void validate(String type, Map<String, Object> config) {
        switch (type) {
            case "INFO_CARDS" -> infoCards(config);
            case "IMAGE_GALLERY" -> imageGallery(config);
            case "PRODUCT_LIST" -> productList(config);
            case "PRODUCT_CATEGORY_NAV" -> categoryNav(config);
            case "PRODUCT_COLLECTION_SHOWCASE" -> collectionShowcase(config);
            case "PRODUCT_EXPANDABLE_DESCRIPTION" -> expandableDescription(config);
            default -> {
                // loại khác do CmsServiceImpl kiểm
            }
        }
    }

    // ============================== từng loại block ==============================

    private static void infoCards(Map<String, Object> c) {
        header(c);
        Map<String, Object> display = optObject(c, "display", "config.display");
        if (display != null) {
            optIntIn(display, "columns", "config.display.columns", INFO_COLUMNS);
        }
        List<Map<String, Object>> items = requiredObjects(c, "items", "config.items", 1, Integer.MAX_VALUE);
        for (int i = 0; i < items.size(); i++) {
            String p = "config.items[" + i + "]";
            Map<String, Object> it = items.get(i);
            requiredUrl(it, "imageUrl", p + ".imageUrl", false);
            requiredText(it, "title", p + ".title");
            optText(it, "description", p + ".description");
            requiredText(it, "actionText", p + ".actionText");
            requiredUrl(it, "actionUrl", p + ".actionUrl", true);
        }
    }

    private static void imageGallery(Map<String, Object> c) {
        header(c);
        Map<String, Object> display = optObject(c, "display", "config.display");
        if (display != null) {
            optEnum(display, "layoutStyle", "config.display.layoutStyle", GALLERY_LAYOUTS);
        }
        List<Map<String, Object>> items = requiredObjects(c, "items", "config.items", 1, Integer.MAX_VALUE);
        for (int i = 0; i < items.size(); i++) {
            String p = "config.items[" + i + "]";
            Map<String, Object> it = items.get(i);
            requiredUrl(it, "imageUrl", p + ".imageUrl", false);
            optText(it, "label", p + ".label");
            optEnum(it, "actionType", p + ".actionType", GALLERY_ACTIONS);
            optUrl(it, "actionUrl", p + ".actionUrl", true);
            mixMatchSkus(it, p + ".mixMatchSkus");
        }
    }

    private static void mixMatchSkus(Map<String, Object> item, String path) {
        List<Map<String, Object>> skus = optObjects(item, "mixMatchSkus", path);
        for (int i = 0; i < skus.size(); i++) {
            String p = path + "[" + i + "]";
            Object id = skus.get(i).get("variationId");
            if (!(id instanceof String s && !s.isBlank()) && !(id instanceof Number)) {
                throw invalid(p + ".variationId", "bắt buộc (mã variation dạng chuỗi)");
            }
            optNumber(skus.get(i), "sortOrder", p + ".sortOrder");
        }
    }

    private static void productList(Map<String, Object> c) {
        Map<String, Object> head = optObject(c, "header", "config.header");
        if (head != null) {
            optText(head, "title", "config.header.title");
            optText(head, "subtitle", "config.header.subtitle");
        }
        Map<String, Object> src = requiredObject(c, "productDataSource", "config.productDataSource");
        String p = "config.productDataSource";
        String filter = requiredEnum(src, "filterType", p + ".filterType", LIST_SOURCES);
        optEnum(src, "sort", p + ".sort", LIST_SORTS);
        List<String> slugs = optTexts(src, "collectionSlugs", p + ".collectionSlugs");
        if ("COLLECTION".equals(filter) && slugs.isEmpty()) {
            throw invalid(p + ".collectionSlugs", "cần ít nhất 1 bộ sưu tập khi filterType = COLLECTION");
        }
        Map<String, Object> banner = optObject(c, "dataSource", "config.dataSource");
        if (banner != null) {
            optEnum(banner, "filterType", "config.dataSource.filterType", LIST_BANNER_SOURCES);
            for (String key : List.of("cmsPageId", "cmsPageSlug", "cmsPageName")) {
                optText(banner, key, "config.dataSource." + key);
            }
        }
    }

    private static void categoryNav(Map<String, Object> c) {
        Map<String, Object> display = optObject(c, "display", "config.display");
        if (display != null) {
            Integer limit = optInt(display, "limit", "config.display.limit");
            if (limit != null && limit < 0) {
                throw invalid("config.display.limit", "phải >= 0 (0 = không giới hạn)");
            }
        }
    }

    private static void collectionShowcase(Map<String, Object> c) {
        header(c);
        List<Map<String, Object>> items = requiredObjects(c, "items", "config.items", 1, SHOWCASE_MAX_ITEMS);
        for (int i = 0; i < items.size(); i++) {
            String p = "config.items[" + i + "]";
            requiredText(items.get(i), "title", p + ".title");
            requiredUrl(items.get(i), "imageUrl", p + ".imageUrl", false);
            optUrl(items.get(i), "actionUrl", p + ".actionUrl", true);
        }
    }

    private static void expandableDescription(Map<String, Object> c) {
        requiredText(c, "title", "config.title");
        requiredText(c, "description", "config.description");
        Integer lines = optInt(c, "maxCollapsedLines", "config.maxCollapsedLines");
        if (lines != null && lines < 1) {
            throw invalid("config.maxCollapsedLines", "phải >= 1");
        }
    }

    /** header = {title (bắt buộc), subtitle (tuỳ chọn)} dùng chung INFO_CARDS, IMAGE_GALLERY, COLLECTION_SHOWCASE. */
    private static void header(Map<String, Object> c) {
        Map<String, Object> head = requiredObject(c, "header", "config.header");
        requiredText(head, "title", "config.header.title");
        optText(head, "subtitle", "config.header.subtitle");
    }

    // ============================== helper kiểu dữ liệu ==============================

    private static BusinessException invalid(String field, String problem) {
        String detail = field + " " + problem;
        return BusinessException.badRequest(ErrorCode.CMS_BLOCK_CONFIG_INVALID,
                "Cấu hình block không hợp lệ: " + detail, detail).withField(field);
    }

    private static Map<String, Object> requiredObject(Map<String, Object> parent, String key, String path) {
        Map<String, Object> value = optObject(parent, key, path);
        if (value == null) {
            throw invalid(path, "là bắt buộc (đối tượng JSON)");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optObject(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof Map<?, ?>)) {
            throw invalid(path, "phải là đối tượng JSON");
        }
        return (Map<String, Object>) v;
    }

    private static List<Map<String, Object>> requiredObjects(Map<String, Object> parent, String key, String path,
                                                              int min, int max) {
        if (parent.get(key) == null) {
            throw invalid(path, "là bắt buộc (mảng có ít nhất " + min + " phần tử)");
        }
        List<Map<String, Object>> list = optObjects(parent, key, path);
        if (list.size() < min || list.size() > max) {
            throw invalid(path, max == Integer.MAX_VALUE ? "cần ít nhất " + min + " phần tử"
                    : "cần từ " + min + " đến " + max + " phần tử");
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> optObjects(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw invalid(path, "phải là mảng");
        }
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?>)) {
                throw invalid(path + "[" + i + "]", "phải là đối tượng JSON");
            }
        }
        return (List<Map<String, Object>>) list;
    }

    private static List<String> optTexts(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
            throw invalid(path, "phải là mảng chuỗi");
        }
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof String s) || s.isBlank()) {
                throw invalid(path + "[" + i + "]", "phải là chuỗi không rỗng");
            }
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static String requiredText(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw invalid(path, v == null || v instanceof String ? "là bắt buộc (chuỗi không rỗng)" : "phải là chuỗi");
        }
        return s;
    }

    private static void optText(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (v != null && !(v instanceof String)) {
            throw invalid(path, "phải là chuỗi");
        }
    }

    private static String requiredEnum(Map<String, Object> parent, String key, String path, Set<String> allowed) {
        String value = requiredText(parent, key, path);
        checkEnum(value, path, allowed);
        return value;
    }

    private static void optEnum(Map<String, Object> parent, String key, String path, Set<String> allowed) {
        Object v = parent.get(key);
        if (v == null) {
            return;
        }
        if (!(v instanceof String s)) {
            throw invalid(path, "phải là chuỗi");
        }
        checkEnum(s, path, allowed);
    }

    private static void checkEnum(String value, String path, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw invalid(path, "không hợp lệ: " + value + " (" + String.join(" | ", new java.util.TreeSet<>(allowed))
                    + ")");
        }
    }

    private static void optNumber(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (v != null && !(v instanceof Number)) {
            throw invalid(path, "phải là số");
        }
    }

    private static Integer optInt(Map<String, Object> parent, String key, String path) {
        Object v = parent.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                || Math.abs(n.doubleValue()) > Integer.MAX_VALUE) {
            throw invalid(path, "phải là số nguyên");
        }
        return n.intValue();
    }

    private static void optIntIn(Map<String, Object> parent, String key, String path, Set<Integer> allowed) {
        Integer v = optInt(parent, key, path);
        if (v != null && !allowed.contains(v)) {
            throw invalid(path, "không hợp lệ: " + v + " (" + allowed.stream().sorted().toList() + ")");
        }
    }

    // ============================== URL ==============================

    private static void requiredUrl(Map<String, Object> parent, String key, String path, boolean allowLink) {
        checkUrl(requiredText(parent, key, path), path, allowLink);
    }

    private static void optUrl(Map<String, Object> parent, String key, String path, boolean allowLink) {
        Object v = parent.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            return;
        }
        if (!(v instanceof String s)) {
            throw invalid(path, "phải là chuỗi");
        }
        checkUrl(s, path, allowLink);
    }

    /**
     * Chấp nhận http(s)://..., đường dẫn tương đối bắt đầu bằng "/" (không phải "//"); với link điều hướng còn
     * chấp nhận "#neo", mailto:, tel:. Từ chối javascript:, data:, vbscript:, file:, ký tự điều khiển/khoảng trắng.
     */
    private static void checkUrl(String raw, String path, boolean allowLink) {
        String url = raw.trim();
        if (url.length() > MAX_URL_LENGTH) {
            throw invalid(path, "quá dài (tối đa " + MAX_URL_LENGTH + " ký tự)");
        }
        if (BAD_URL_CHARS.matcher(url).find()) {
            throw invalid(path, "chứa ký tự không an toàn");
        }
        if (SCHEME.matcher(url).matches()) {
            String scheme = url.substring(0, url.indexOf(':')).toLowerCase(Locale.ROOT);
            boolean ok = scheme.equals("http") || scheme.equals("https")
                    || (allowLink && (scheme.equals("mailto") || scheme.equals("tel")));
            if (!ok) {
                throw invalid(path, "dùng giao thức không được phép: " + scheme);
            }
            return;
        }
        boolean relative = url.startsWith("/") && !url.startsWith("//");
        if (!relative && !(allowLink && url.startsWith("#"))) {
            throw invalid(path, "phải là http(s):// hoặc đường dẫn bắt đầu bằng /");
        }
    }
}

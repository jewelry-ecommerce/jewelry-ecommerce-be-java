package com.pk.core.business.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tool strict. Định nghĩa không đổi giữa các câu để nằm trong prefix cache. */
public final class AssistantTools {

    public static final String SEARCH = "search_products";
    public static final String GET = "get_product";

    private AssistantTools() {
    }

    public static List<Map<String, Object>> definitions() {
        return definitions(true);
    }

    public static List<Map<String, Object>> definitions(boolean strict) {
        return List.of(tool(SEARCH, "Tìm trang sức đang bán theo từ khóa ngắn.", querySchema(strict), strict),
                tool(GET, "Lấy một sản phẩm theo slug trên link san-pham.", slugSchema(strict), strict));
    }

    public static Map<String, Object> replySchema() {
        return replySchema(true);
    }

    public static Map<String, Object> replySchema(boolean strict) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("reply", "productIds"));
        schema.put("properties", replyProperties());
        if (strict) {
            schema.put("additionalProperties", false);
        }
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("name", "shop_reply");
        format.put("schema", schema);
        if (strict) {
            format.put("strict", true);
        }
        return Map.of("type", "json_schema", "json_schema", format);
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters, boolean strict) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        if (strict) {
            function.put("strict", true);
        }
        function.put("parameters", parameters);
        return Map.of("type", "function", "function", function);
    }

    private static Map<String, Object> querySchema(boolean strict) {
        return objectSchema("query", "Từ khóa ngắn, ví dụ nhẫn hoặc lắc tay.", strict);
    }

    private static Map<String, Object> slugSchema(boolean strict) {
        return objectSchema("slug", "Đoạn slug sau san-pham/.", strict);
    }

    private static Map<String, Object> objectSchema(String field, String description, boolean strict) {
        Map<String, Object> property = Map.of("type", "string", "description", description);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of(field));
        schema.put("properties", Map.of(field, property));
        if (strict) {
            schema.put("additionalProperties", false);
        }
        return schema;
    }

    private static Map<String, Object> replyProperties() {
        Map<String, Object> reply = Map.of("type", "string");
        Map<String, Object> ids = Map.of("type", "array", "items", Map.of("type", "integer"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reply", reply);
        properties.put("productIds", ids);
        return properties;
    }
}

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
        return List.of(tool(SEARCH, "Tìm trang sức đang bán theo từ khóa ngắn.", querySchema()),
                tool(GET, "Lấy một sản phẩm theo slug trên link san-pham.", slugSchema()));
    }

    public static Map<String, Object> replySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("reply", "productIds"));
        schema.put("properties", replyProperties());
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("name", "shop_reply");
        format.put("strict", true);
        format.put("schema", schema);
        return Map.of("type", "json_schema", "json_schema", format);
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("strict", true);
        function.put("parameters", parameters);
        return Map.of("type", "function", "function", function);
    }

    private static Map<String, Object> querySchema() {
        return objectSchema("query", "Từ khóa ngắn, ví dụ nhẫn hoặc lắc tay.");
    }

    private static Map<String, Object> slugSchema() {
        return objectSchema("slug", "Đoạn slug sau san-pham/.");
    }

    private static Map<String, Object> objectSchema(String field, String description) {
        Map<String, Object> property = Map.of("type", "string", "description", description);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(field));
        schema.put("properties", Map.of(field, property));
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

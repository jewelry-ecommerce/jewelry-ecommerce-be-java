package com.pk.core.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Đọc JSON trợ lý và chỉ giữ productId có trong catalog. */
public final class AssistantReplyParser {

    public static final int MAX_PRODUCTS = 4;
    public static final int MAX_REPLY = 500;

    private final ObjectMapper mapper;

    public AssistantReplyParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Draft parse(String json, Set<Long> allowed) throws Exception {
        JsonNode root = mapper.readTree(unwrap(json));
        String reply = clip(root.path("reply").asText(""));
        return new Draft(reply, productIds(root.path("productIds"), allowed));
    }

    private static List<Long> productIds(JsonNode node, Set<Long> allowed) {
        List<Long> ids = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return ids;
        }
        for (JsonNode item : node) {
            if (ids.size() >= MAX_PRODUCTS || !item.isNumber()) {
                continue;
            }
            long id = item.asLong();
            if (allowed.contains(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    static String unwrap(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int start = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        if (start < 0 || end <= start) {
            return trimmed;
        }
        return trimmed.substring(start + 1, end).trim();
    }

    static String clip(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= MAX_REPLY ? trimmed : trimmed.substring(0, MAX_REPLY);
    }

    public record Draft(String reply, List<Long> productIds) {
    }
}

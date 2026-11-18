package com.pk.core.business.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** System đứng trước, câu khách đứng sau. Tool chạy xong mới thêm kết quả. */
public final class AssistantTranscript {

    private AssistantTranscript() {
    }

    public static List<Map<String, Object>> start(String system, String user) {
        return start(system, List.of(), user);
    }

    public static List<Map<String, Object>> start(String system, List<AssistantPrior> prior, String user) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(text("system", system));
        for (AssistantPrior item : prior) {
            messages.add(text(item.role(), item.content()));
        }
        messages.add(text("user", user == null ? "" : user));
        return messages;
    }

    public static Map<String, Object> text(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
}

package com.pk.core.business.ai;

import com.pk.core.model.dto.request.AssistantHistoryItemDto;
import com.pk.core.model.dto.request.AssistantHistoryLimits;

import java.util.ArrayList;
import java.util.List;

/** Giữ vài lượt gần nhất. Không nhận role system từ trình duyệt. */
public final class AssistantContext {

    private AssistantContext() {
    }

    public static List<AssistantPrior> from(List<AssistantHistoryItemDto> history) {
        List<AssistantPrior> prior = new ArrayList<>();
        if (history == null) {
            return prior;
        }
        for (AssistantHistoryItemDto item : history) {
            AssistantPrior clean = clean(item);
            if (clean != null) {
                prior.add(clean);
            }
        }
        return prior;
    }

    public static List<AssistantPrior> latest(List<AssistantPrior> history, int turns) {
        int limit = Math.max(0, turns) * 2;
        int from = Math.max(0, history.size() - limit);
        return List.copyOf(history.subList(from, history.size()));
    }

    private static AssistantPrior clean(AssistantHistoryItemDto item) {
        if (item == null || !customerRole(item.getRole()) || item.getContent() == null) {
            return null;
        }
        String content = item.getContent().trim();
        if (content.isEmpty()) {
            return null;
        }
        int max = AssistantHistoryLimits.MAX_CONTENT;
        return new AssistantPrior(item.getRole(), content.length() <= max ? content : content.substring(0, max));
    }

    private static boolean customerRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }
}

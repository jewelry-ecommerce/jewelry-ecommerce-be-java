package com.pk.core.test.business;

import com.pk.core.business.ai.AssistantSystemPrompt;
import com.pk.core.business.ai.AssistantTools;
import com.pk.core.business.ai.AssistantTranscript;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantTranscriptTest {

    @Test
    void systemPromptStaysAheadOfTheCustomerMessage() {
        String system = AssistantSystemPrompt.text("Phí giao hàng hiện là 0 đồng.");
        List<Map<String, Object>> messages = AssistantTranscript.start(system, "tôi muốn mua nhẫn");
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("user", messages.get(1).get("role"));
        assertFalse(String.valueOf(messages.get(0).get("content")).contains("tôi muốn mua nhẫn"));
        assertTrue(String.valueOf(messages.get(0).get("content")).contains("search_products"));
    }

    @Test
    void toolsStayStrictSoTheModelCannotInventFields() {
        Map<String, Object> tool = AssistantTools.definitions().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) tool.get("function");
        assertEquals(Boolean.TRUE, function.get("strict"));
        assertEquals("search_products", function.get("name"));
    }
}

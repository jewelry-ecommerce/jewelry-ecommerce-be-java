package com.pk.core.business.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Một lượt hỗ trợ: system đứng yên, tool lấy hàng, structured output cho câu khách thấy. */
@Service
public class ShopAssistantService {

    private final AiProperties properties;
    private final AssistantCompletionClient client;
    private final AssistantToolHandler tools;
    private final AssistantReplyParser parser;
    private final ObjectMapper mapper;

    public ShopAssistantService(AiProperties properties, AssistantCompletionClient client,
                                AssistantToolHandler tools, ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.tools = tools;
        this.parser = new AssistantReplyParser(mapper);
        this.mapper = mapper;
    }

    public AssistantTurn answer(String message) throws Exception {
        List<Map<String, Object>> messages = AssistantTranscript.start(
                AssistantSystemPrompt.text(properties.getShopBrief()), message);
        List<AiCandidate> found = new ArrayList<>();
        JsonNode assistant = ask(messages, found);
        return turn(assistant, found);
    }

    private JsonNode ask(List<Map<String, Object>> messages, List<AiCandidate> found) {
        JsonNode assistant = null;
        int rounds = Math.max(0, properties.getAssistantMaxToolRounds());
        for (int round = 0; round < rounds; round++) {
            assistant = client.complete(messages, true);
            if (!hasTools(assistant)) {
                return assistant;
            }
            messages.add(assistantMessage(assistant));
            appendTools(messages, assistant, found);
            assistant = null;
        }
        return client.complete(messages, false);
    }

    private void appendTools(List<Map<String, Object>> messages, JsonNode assistant, List<AiCandidate> found) {
        for (JsonNode call : assistant.path("tool_calls")) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("role", "tool");
            tool.put("tool_call_id", call.path("id").asText("call"));
            tool.put("content", tools.run(call.path("function").path("name").asText(""),
                    call.path("function").path("arguments").asText("{}"), found));
            messages.add(tool);
        }
    }

    private Map<String, Object> assistantMessage(JsonNode message) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("role", "assistant");
        copy.put("content", textContent(message));
        if (hasTools(message)) {
            copy.put("tool_calls", mapper.convertValue(message.path("tool_calls"), new TypeReference<List<Map<String, Object>>>() {
            }));
        }
        return copy;
    }

    private AssistantTurn turn(JsonNode assistant, List<AiCandidate> found) throws Exception {
        String content = textContent(assistant);
        Set<Long> allowed = found.stream().map(AiCandidate::productId).collect(Collectors.toSet());
        try {
            return new AssistantTurn(parser.parse(content, allowed), found);
        } catch (Exception ex) {
            return new AssistantTurn(new AssistantReplyParser.Draft(content, List.of()), found);
        }
    }

    private static boolean hasTools(JsonNode message) {
        return message != null && message.path("tool_calls").isArray() && !message.path("tool_calls").isEmpty();
    }

    private static String textContent(JsonNode message) {
        JsonNode content = message == null ? null : message.path("content");
        if (content == null || content.isNull() || content.isMissingNode()) {
            return "";
        }
        if (!content.isArray()) {
            return content.asText("");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            text.append(part.path("text").asText(""));
        }
        return text.toString();
    }
}

package com.pk.core.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chạy tool trên catalog thật. Model không được tự điền giá. */
@Component
public class AssistantToolHandler {

    private final AiRecommendationOrchestrator orchestrator;
    private final ObjectMapper mapper;

    public AssistantToolHandler(AiRecommendationOrchestrator orchestrator, ObjectMapper mapper) {
        this.orchestrator = orchestrator;
        this.mapper = mapper;
    }

    public String run(String name, String arguments, List<AiCandidate> sink) {
        try {
            if (AssistantTools.SEARCH.equals(name)) {
                return search(arguments, sink);
            }
            if (AssistantTools.GET.equals(name)) {
                return load(arguments, sink);
            }
            return "{\"error\":\"unknown_tool\"}";
        } catch (Exception ex) {
            return "{\"error\":\"tool_failed\"}";
        }
    }

    private String search(String arguments, List<AiCandidate> sink) throws Exception {
        String query = field(arguments, "query");
        AssistantRoute route = AssistantQuestionRouter.route(query);
        String keyword = route.keyword() == null || route.keyword().isBlank() ? query : route.keyword();
        return write(orchestrator.forAssistant(null, keyword), sink);
    }

    private String load(String arguments, List<AiCandidate> sink) throws Exception {
        return write(orchestrator.forAssistant(field(arguments, "slug"), ""), sink);
    }

    private String field(String arguments, String name) throws Exception {
        String json = arguments == null || arguments.isBlank() ? "{}" : arguments;
        return mapper.readTree(json).path(name).asText("").trim();
    }

    private String write(List<AiCandidate> found, List<AiCandidate> sink) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (found == null) {
            return "[]";
        }
        for (AiCandidate candidate : found) {
            remember(sink, candidate);
            rows.add(row(candidate));
        }
        return mapper.writeValueAsString(rows);
    }

    private static void remember(List<AiCandidate> sink, AiCandidate candidate) {
        for (AiCandidate current : sink) {
            if (current.productId() == candidate.productId()) {
                return;
            }
        }
        sink.add(candidate);
    }

    private static Map<String, Object> row(AiCandidate candidate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", candidate.productId());
        row.put("name", candidate.name());
        row.put("price", candidate.price());
        row.put("category", candidate.categoryName());
        row.put("material", candidate.material());
        row.put("stock", candidate.stock());
        row.put("slug", candidate.slug());
        return row;
    }
}

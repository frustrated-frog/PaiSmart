package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContextBudgetServiceTest {

    @Test
    void compactsOldGroupsWithoutOrphaningToolMessages() {
        AgenticRagProperties properties = new AgenticRagProperties();
        properties.getContext().setMaxPromptChars(4000);
        properties.getContext().setMaxHistoricalMessageChars(3000);
        properties.getContext().setMaxToolObservationChars(2000);
        AgentContextBudgetService service = new AgentContextBudgetService(properties, new ObjectMapper());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", "rules"));
        messages.add(message("user", "old-question-" + "x".repeat(1800)));
        Map<String, Object> assistantCall = new LinkedHashMap<>(message("assistant", ""));
        assistantCall.put("tool_calls", List.of(Map.of("id", "call-1")));
        messages.add(assistantCall);
        messages.add(Map.of("role", "tool", "tool_call_id", "call-1", "content", "old-result-" + "y".repeat(1800)));
        messages.add(message("user", "new-question-" + "z".repeat(1800)));

        AgentContextBudgetService.CompactionResult result = service.compact(messages);

        assertEquals("system", result.messages().get(0).get("role"));
        assertEquals("user", result.messages().get(result.messages().size() - 1).get("role"));
        assertTrue(result.droppedMessages() > 0);
        for (int index = 0; index < result.messages().size(); index++) {
            if ("tool".equals(result.messages().get(index).get("role"))) {
                assertTrue(index > 0);
                assertEquals("assistant", result.messages().get(index - 1).get("role"));
            }
        }
    }

    @Test
    void truncatesOversizedToolObservations() {
        AgenticRagProperties properties = new AgenticRagProperties();
        properties.getContext().setMaxToolObservationChars(1000);
        AgentContextBudgetService service = new AgentContextBudgetService(properties, new ObjectMapper());

        String compacted = service.compactToolObservation("a".repeat(2000));

        assertEquals(1000, compacted.length());
        assertTrue(compacted.endsWith("…[context compacted]"));
    }

    private Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }
}

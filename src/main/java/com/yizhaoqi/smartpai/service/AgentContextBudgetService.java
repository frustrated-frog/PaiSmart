package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct 上下文中间件：按消息组裁剪，避免拆散 assistant tool_call 与对应 tool observation。
 */
@Service
public class AgentContextBudgetService {

    private static final String TRUNCATED_SUFFIX = "\n…[context compacted]";
    private final AgenticRagProperties properties;
    private final ObjectMapper objectMapper;

    public AgentContextBudgetService(AgenticRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CompactionResult compact(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return new CompactionResult(List.of(), 0, 0, 0);
        }
        int maxChars = Math.max(4000, properties.getContext().getMaxPromptChars());
        List<Map<String, Object>> normalized = messages.stream().map(this::normalizeMessage).toList();
        int originalChars = size(normalized);
        if (originalChars <= maxChars) {
            return new CompactionResult(normalized, originalChars, originalChars, 0);
        }

        Map<String, Object> system = "system".equals(role(normalized.get(0))) ? normalized.get(0) : null;
        int start = system == null ? 0 : 1;
        List<List<Map<String, Object>>> groups = groupMessages(normalized.subList(start, normalized.size()));
        List<List<Map<String, Object>>> selected = new ArrayList<>();
        int used = system == null ? 0 : size(List.of(system));
        for (int index = groups.size() - 1; index >= 0; index--) {
            List<Map<String, Object>> group = groups.get(index);
            int groupSize = size(group);
            if (used + groupSize <= maxChars || selected.isEmpty()) {
                selected.add(group);
                used += groupSize;
            }
        }
        Collections.reverse(selected);
        List<Map<String, Object>> compacted = new ArrayList<>();
        if (system != null) {
            compacted.add(system);
        }
        selected.forEach(compacted::addAll);
        return new CompactionResult(
                List.copyOf(compacted),
                originalChars,
                size(compacted),
                Math.max(0, normalized.size() - compacted.size())
        );
    }

    public String compactToolObservation(String content) {
        if (content == null) {
            return "";
        }
        int maxChars = Math.max(1000, properties.getContext().getMaxToolObservationChars());
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, Math.max(0, maxChars - TRUNCATED_SUFFIX.length())) + TRUNCATED_SUFFIX;
    }

    private Map<String, Object> normalizeMessage(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        Object content = copy.get("content");
        if (content instanceof String text && !"system".equals(role(copy)) && !"tool".equals(role(copy))) {
            int max = Math.max(400, properties.getContext().getMaxHistoricalMessageChars());
            if (text.length() > max) {
                copy.put("content", text.substring(0, Math.max(0, max - TRUNCATED_SUFFIX.length())) + TRUNCATED_SUFFIX);
            }
        }
        if (content instanceof String text && "tool".equals(role(copy))) {
            copy.put("content", compactToolObservation(text));
        }
        return copy;
    }

    private List<List<Map<String, Object>>> groupMessages(List<Map<String, Object>> messages) {
        List<List<Map<String, Object>>> groups = new ArrayList<>();
        for (int index = 0; index < messages.size();) {
            Map<String, Object> current = messages.get(index);
            List<Map<String, Object>> group = new ArrayList<>();
            group.add(current);
            index++;
            if ("assistant".equals(role(current)) && current.containsKey("tool_calls")) {
                while (index < messages.size() && "tool".equals(role(messages.get(index)))) {
                    group.add(messages.get(index));
                    index++;
                }
            }
            groups.add(List.copyOf(group));
        }
        return groups;
    }

    private String role(Map<String, Object> message) {
        return String.valueOf(message.getOrDefault("role", ""));
    }

    private int size(List<Map<String, Object>> messages) {
        try {
            return objectMapper.writeValueAsString(messages).length();
        } catch (Exception exception) {
            return messages.toString().length();
        }
    }

    public record CompactionResult(List<Map<String, Object>> messages,
                                   int originalChars,
                                   int compactedChars,
                                   int droppedMessages) {
    }
}

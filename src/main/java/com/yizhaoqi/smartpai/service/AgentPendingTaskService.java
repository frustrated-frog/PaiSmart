package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentPendingTask;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.repository.AgentPendingTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 持久化澄清等待任务，并把用户短回复合并回原问题。 */
@Service
public class AgentPendingTaskService {

    private static final String ACTIVE = "WAITING_CLARIFICATION";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final AgentPendingTaskRepository repository;
    private final ObjectMapper objectMapper;

    public AgentPendingTaskService(AgentPendingTaskRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentPendingTask create(String generationId,
                                   String userId,
                                   String conversationId,
                                   QueryPlan plan,
                                   String resumeNode) {
        LocalDateTime now = LocalDateTime.now();
        repository.findByUserIdAndConversationIdAndStatus(userId, conversationId, ACTIVE).forEach(existing -> {
            existing.setStatus("CANCELLED");
            existing.setUpdatedAt(now);
            repository.save(existing);
        });

        AgentPendingTask task = new AgentPendingTask();
        task.setGenerationId(generationId);
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setStatus(ACTIVE);
        task.setOriginalQuery(plan.originalQuery());
        task.setIntent(plan.intent().name());
        task.setKnownSlotsJson(writeJson(plan.knownSlots()));
        task.setMissingSlotsJson(writeJson(plan.missingSlots()));
        task.setQuestion(plan.clarificationQuestion());
        task.setOptionsJson(writeJson(plan.clarificationOptions()));
        task.setResumeNode(resumeNode == null || resumeNode.isBlank() ? "QUERY_PLANNING" : resumeNode);
        task.setExpiresAt(now.plus(DEFAULT_TTL));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return repository.save(task);
    }

    @Transactional
    public Optional<ResolvedClarification> consume(String userId,
                                                   String conversationId,
                                                   String userAnswer) {
        Optional<AgentPendingTask> optional = repository
                .findTopByUserIdAndConversationIdAndStatusOrderByIdDesc(userId, conversationId, ACTIVE);
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        AgentPendingTask task = optional.get();
        LocalDateTime now = LocalDateTime.now();
        if (task.getExpiresAt().isBefore(now)) {
            task.setStatus("EXPIRED");
            task.setUpdatedAt(now);
            repository.save(task);
            return Optional.empty();
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return Optional.empty();
        }

        Map<String, String> slots = readMap(task.getKnownSlotsJson());
        List<String> missingSlots = readList(task.getMissingSlotsJson());
        mergeSlots(slots, missingSlots, userAnswer.trim());
        task.setKnownSlotsJson(writeJson(slots));
        task.setMissingSlotsJson("[]");
        task.setStatus("RESUMED");
        task.setUpdatedAt(now);
        repository.save(task);

        String mergedQuery = task.getOriginalQuery()
                + "\n\n用户对澄清问题“" + task.getQuestion() + "”的补充：" + userAnswer.trim();
        return Optional.of(new ResolvedClarification(
                task.getId(),
                task.getGenerationId(),
                task.getOriginalQuery(),
                userAnswer.trim(),
                mergedQuery,
                task.getIntent(),
                slots,
                task.getResumeNode()
        ));
    }

    private void mergeSlots(Map<String, String> slots, List<String> missingSlots, String answer) {
        if (missingSlots.size() == 1) {
            slots.put(missingSlots.get(0), answer);
            return;
        }
        String[] parts = answer.split("[，,、;；]");
        if (!missingSlots.isEmpty() && parts.length == missingSlots.size()) {
            for (int index = 0; index < missingSlots.size(); index++) {
                slots.put(missingSlots.get(index), parts[index].trim());
            }
            return;
        }
        slots.put("clarificationAnswer", answer);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化 Pending Task", exception);
        }
    }

    private Map<String, String> readMap(String value) {
        try {
            return new LinkedHashMap<>(objectMapper.readValue(value, new TypeReference<Map<String, String>>() { }));
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> readList(String value) {
        try {
            return new ArrayList<>(objectMapper.readValue(value, new TypeReference<List<String>>() { }));
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    public record ResolvedClarification(
            long pendingTaskId,
            String sourceGenerationId,
            String originalQuery,
            String userAnswer,
            String mergedQuery,
            String intent,
            Map<String, String> slots,
            String resumeNode
    ) {
        public ResolvedClarification {
            slots = slots == null ? Map.of() : Map.copyOf(slots);
        }
    }
}

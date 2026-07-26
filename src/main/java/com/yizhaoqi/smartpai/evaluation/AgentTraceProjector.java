package com.yizhaoqi.smartpai.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentRun;
import com.yizhaoqi.smartpai.model.AgentStep;
import com.yizhaoqi.smartpai.model.AgentToolCall;
import com.yizhaoqi.smartpai.repository.AgentRunRepository;
import com.yizhaoqi.smartpai.repository.AgentStepRepository;
import com.yizhaoqi.smartpai.repository.AgentToolCallRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将 MySQL 中的 Run、Step 与 Tool Ledger 投影为不可由客户端伪造的评测信号。 */
@Service
public class AgentTraceProjector {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
    private final AgentRunRepository runRepository;
    private final AgentStepRepository stepRepository;
    private final AgentToolCallRepository toolCallRepository;
    private final ObjectMapper objectMapper;

    public AgentTraceProjector(AgentRunRepository runRepository,
                               AgentStepRepository stepRepository,
                               AgentToolCallRepository toolCallRepository,
                               ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.toolCallRepository = toolCallRepository;
        this.objectMapper = objectMapper;
    }

    public ProjectedTrace project(String generationId, String userId) {
        AgentRun run = runRepository.findById(generationId)
                .filter(item -> userId.equals(item.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("Agent 运行记录不存在"));
        List<AgentStep> rawSteps = stepRepository.findByGenerationIdOrderByIdAsc(generationId);
        List<AgentToolCall> toolCalls = toolCallRepository.findByGenerationIdOrderByIdAsc(generationId);

        Map<String, AgentStep> latestByStep = new LinkedHashMap<>();
        rawSteps.forEach(step -> latestByStep.put(step.getStepId(), step));
        List<AgentStep> steps = new ArrayList<>(latestByStep.values());
        List<Map<String, Object>> metadata = steps.stream().map(this::metadata).toList();

        boolean clarification = metadata.stream().anyMatch(item ->
                "WAITING_CLARIFICATION".equals(string(item.get("terminalReason"))));
        List<String> clarificationSlots = metadata.stream()
                .flatMap(item -> stringList(item.get("missingSlots")).stream())
                .distinct()
                .toList();
        String intent = metadata.stream().map(this::intent).filter(value -> !value.isBlank()).findFirst().orElse("");
        String evidenceStatus = metadata.stream().map(this::evidenceStatus)
                .filter(value -> !value.isBlank()).reduce((first, last) -> last).orElse("");
        boolean conflict = metadata.stream().anyMatch(this::hasConflict);

        List<AgentEvaluationService.ToolCall> tools = toolCalls.stream()
                .map(call -> new AgentEvaluationService.ToolCall(call.getToolName(), call.getActionFingerprint()))
                .toList();
        long uniqueActions = toolCalls.stream().map(AgentToolCall::getActionFingerprint).distinct().count();
        int duplicateActions = Math.max(0, toolCalls.size() - (int) uniqueActions);
        int reused = (int) toolCalls.stream().filter(call -> "REUSED".equals(call.getStatus())).count();
        int writeActions = (int) toolCalls.stream().filter(call -> "WRITE".equals(call.getToolEffect())).count();
        int duplicateSideEffects = duplicateSideEffects(toolCalls);
        long latencyMs = run.getCreatedAt() == null || run.getFinishedAt() == null
                ? 0L
                : Math.max(0L, Duration.between(run.getCreatedAt(), run.getFinishedAt()).toMillis());

        return new ProjectedTrace(
                clarification,
                clarificationSlots,
                steps.stream().map(AgentStep::getStage).filter(value -> value != null && !value.isBlank()).toList(),
                tools,
                citationIds(run.getAnswer()),
                List.of(),
                duplicateActions,
                toolCalls.size(),
                string(run.getTerminalReason()),
                intent,
                evidenceStatus,
                conflict,
                run.getRetryOfGenerationId() != null,
                run.getRetryOfGenerationId() != null && "COMPLETED".equals(run.getStatus()),
                reused,
                duplicateSideEffects,
                writeActions,
                latencyMs,
                value(run.getPromptTokens()),
                value(run.getCompletionTokens())
        );
    }

    private int duplicateSideEffects(List<AgentToolCall> calls) {
        Map<String, Integer> successfulWrites = new LinkedHashMap<>();
        calls.stream()
                .filter(call -> "WRITE".equals(call.getToolEffect()) && "SUCCESS".equals(call.getStatus()))
                .forEach(call -> successfulWrites.merge(call.getActionFingerprint(), 1, Integer::sum));
        return successfulWrites.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
    }

    private List<String> citationIds(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return List.copyOf(values);
    }

    private Map<String, Object> metadata(AgentStep step) {
        if (step.getMetadataJson() == null || step.getMetadataJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(step.getMetadataJson(), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String intent(Map<String, Object> metadata) {
        return string(nested(metadata, "retrievalTrace", "queryPlan", "intent"));
    }

    private String evidenceStatus(Map<String, Object> metadata) {
        return string(nested(metadata, "evidenceAssessment", "status"));
    }

    private boolean hasConflict(Map<String, Object> metadata) {
        return !stringList(nested(metadata, "evidenceAssessment", "conflicts")).isEmpty()
                || "CONFLICTED".equals(evidenceStatus(metadata));
    }

    private Object nested(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::string).filter(item -> !item.isBlank()).toList();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    public record ProjectedTrace(boolean actualClarification,
                                 List<String> actualClarificationSlots,
                                 List<String> trajectory,
                                 List<AgentEvaluationService.ToolCall> tools,
                                 List<String> citationIds,
                                 List<String> supportedClaimIds,
                                 int duplicateActionCount,
                                 int totalActionCount,
                                 String terminalReason,
                                 String actualIntent,
                                 String actualEvidenceStatus,
                                 boolean actualConflict,
                                 boolean recoveryAttempt,
                                 boolean recoverySucceeded,
                                 int reusedStepCount,
                                 int duplicateSideEffectCount,
                                 int writeActionCount,
                                 long latencyMs,
                                 int promptTokens,
                                 int completionTokens) {
    }
}

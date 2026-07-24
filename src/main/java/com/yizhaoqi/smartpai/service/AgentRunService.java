package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentCheckpoint;
import com.yizhaoqi.smartpai.model.AgentRun;
import com.yizhaoqi.smartpai.model.AgentStep;
import com.yizhaoqi.smartpai.repository.AgentCheckpointRepository;
import com.yizhaoqi.smartpai.repository.AgentRunRepository;
import com.yizhaoqi.smartpai.repository.AgentStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MySQL 持久化 Agent 事件账本。Redis 只承担热状态与断线续传。 */
@Service
public class AgentRunService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRunService.class);
    private final AgentRunRepository runRepository;
    private final AgentStepRepository stepRepository;
    private final AgentCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;

    public AgentRunService(AgentRunRepository runRepository,
                           AgentStepRepository stepRepository,
                           AgentCheckpointRepository checkpointRepository,
                           ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.checkpointRepository = checkpointRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void start(String generationId, String userId, String conversationId, String question) {
        if (runRepository.existsById(generationId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentRun run = new AgentRun();
        run.setGenerationId(generationId);
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setQuestion(question);
        run.setStatus("RUNNING");
        run.setCurrentStage("intake");
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runRepository.save(run);
        checkpoint(generationId, "RUN_STARTED", Map.of("question", question));
    }

    @Transactional
    public void appendStep(String generationId,
                           String stepId,
                           String stage,
                           String status,
                           String title,
                           String detail,
                           String toolName,
                           Map<String, Object> metadata) {
        Optional<AgentRun> optionalRun = runRepository.findById(generationId);
        if (optionalRun.isEmpty()) {
            return;
        }
        AgentStep step = new AgentStep();
        step.setGenerationId(generationId);
        step.setStepId(stepId);
        step.setStage(stage);
        step.setStatus(status);
        step.setTitle(title);
        step.setDetail(detail);
        step.setToolName(toolName);
        step.setMetadataJson(writeJson(metadata));
        step.setOccurredAt(LocalDateTime.now());
        stepRepository.save(step);

        AgentRun run = optionalRun.get();
        run.setCurrentStage(stage);
        run.setUpdatedAt(LocalDateTime.now());
        runRepository.save(run);
        if ("completed".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
            checkpoint(generationId, "STEP_TERMINAL", Map.of(
                    "stepId", stepId,
                    "stage", stage,
                    "status", status,
                    "toolName", toolName == null ? "" : toolName
            ));
        }
    }

    @Transactional
    public void complete(String generationId, String answer, int promptTokens, int completionTokens) {
        updateTerminal(generationId, "COMPLETED", answer, null, promptTokens, completionTokens);
    }

    @Transactional
    public void fail(String generationId, String error) {
        updateTerminal(generationId, "FAILED", null, error, 0, 0);
    }

    @Transactional
    public void cancel(String generationId) {
        updateTerminal(generationId, "CANCELLED", null, null, 0, 0);
    }

    @Transactional(readOnly = true)
    public Optional<RunDetail> get(String generationId) {
        return runRepository.findById(generationId).map(run -> new RunDetail(
                run,
                stepRepository.findByGenerationIdOrderByIdAsc(generationId),
                checkpointRepository.findTopByGenerationIdOrderByIdDesc(generationId).orElse(null)
        ));
    }

    private void updateTerminal(String generationId,
                                String status,
                                String answer,
                                String error,
                                int promptTokens,
                                int completionTokens) {
        runRepository.findById(generationId).ifPresent(run -> {
            run.setStatus(status);
            run.setAnswer(answer);
            run.setErrorMessage(error);
            run.setPromptTokens(promptTokens);
            run.setCompletionTokens(completionTokens);
            run.setUpdatedAt(LocalDateTime.now());
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            checkpoint(generationId, "RUN_" + status, Map.of(
                    "status", status,
                    "answerChars", answer == null ? 0 : answer.length(),
                    "error", error == null ? "" : error
            ));
        });
    }

    private void checkpoint(String generationId, String type, Map<String, Object> state) {
        try {
            AgentCheckpoint checkpoint = new AgentCheckpoint();
            checkpoint.setGenerationId(generationId);
            checkpoint.setCheckpointType(type);
            checkpoint.setStateJson(writeJson(state));
            checkpoint.setCreatedAt(LocalDateTime.now());
            checkpointRepository.save(checkpoint);
        } catch (Exception exception) {
            logger.warn("Agent checkpoint 写入失败: generationId={}, type={}", generationId, type, exception);
        }
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(value));
        } catch (Exception exception) {
            return "{\"serializationError\":true}";
        }
    }

    public record RunDetail(AgentRun run, List<AgentStep> steps, AgentCheckpoint latestCheckpoint) {
    }
}

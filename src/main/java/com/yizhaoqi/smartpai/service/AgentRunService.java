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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

/** MySQL 持久化 Agent 事件账本。Redis 只承担热状态与断线续传。 */
@Service
public class AgentRunService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRunService.class);
    private final AgentRunRepository runRepository;
    private final AgentStepRepository stepRepository;
    private final AgentCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;
    private final AgentErrorSanitizer errorSanitizer;

    public AgentRunService(AgentRunRepository runRepository,
                           AgentStepRepository stepRepository,
                           AgentCheckpointRepository checkpointRepository,
                           ObjectMapper objectMapper,
                           AgentErrorSanitizer errorSanitizer) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.checkpointRepository = checkpointRepository;
        this.objectMapper = objectMapper;
        this.errorSanitizer = errorSanitizer;
    }

    @Transactional
    public void start(String generationId, String userId, String conversationId, String question) {
        startInternal(generationId, userId, conversationId, question, null, 1);
    }

    @Transactional
    public void startRetry(String generationId, RetryCandidate source) {
        startInternal(
                generationId,
                source.userId(),
                source.conversationId(),
                source.question(),
                source.generationId(),
                source.attemptNumber() + 1
        );
    }

    private void startInternal(String generationId,
                               String userId,
                               String conversationId,
                               String question,
                               String retryOfGenerationId,
                               int attemptNumber) {
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
        run.setRetryOfGenerationId(retryOfGenerationId);
        run.setAttemptNumber(attemptNumber);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runRepository.save(run);
        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put("question", question);
        initialState.put("attemptNumber", attemptNumber);
        if (retryOfGenerationId != null) {
            initialState.put("retryOfGenerationId", retryOfGenerationId);
        }
        checkpoint(generationId, retryOfGenerationId == null ? "RUN_STARTED" : "RUN_RETRIED", initialState);
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

    @Transactional(readOnly = true)
    public RetryCandidate getRetryCandidate(String generationId, String userId) {
        AgentRun run = runRepository.findById(generationId)
                .filter(item -> userId.equals(item.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("Agent 运行记录不存在"));
        if (!List.of("FAILED", "INTERRUPTED", "CANCELLED").contains(run.getStatus())) {
            throw new IllegalArgumentException("只有失败、中断或取消的 Agent 运行可以重试");
        }
        int attempt = run.getAttemptNumber() == null ? 1 : run.getAttemptNumber();
        return new RetryCandidate(
                run.getGenerationId(),
                run.getUserId(),
                run.getConversationId(),
                run.getQuestion(),
                run.getCurrentStage(),
                attempt
        );
    }

    @Transactional(readOnly = true)
    public List<RunSummary> listRecoverable(String userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptyList();
        }
        return runRepository.findTop50ByUserIdAndConversationIdOrderByCreatedAtAsc(userId, conversationId).stream()
                .filter(run -> List.of("FAILED", "INTERRUPTED", "CANCELLED").contains(run.getStatus()))
                .map(run -> new RunSummary(
                        run.getGenerationId(),
                        run.getConversationId(),
                        run.getQuestion(),
                        run.getStatus(),
                        run.getCurrentStage(),
                        errorSanitizer.auditMessage(run.getErrorMessage()),
                        run.getRetryOfGenerationId(),
                        run.getAttemptNumber() == null ? 1 : run.getAttemptNumber(),
                        run.getCreatedAt(),
                        run.getUpdatedAt(),
                        stepRepository.findByGenerationIdOrderByIdAsc(run.getGenerationId()).stream()
                                .map(step -> toStepView(run, step))
                                .toList()
                ))
                .toList();
    }

    /**
     * 进程重启后，旧进程中的流式连接和工具 Future 已不可恢复。
     * 将悬空 RUNNING 运行标为 INTERRUPTED，并保留最新 checkpoint 供前端展示和人工重试。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedRuns() {
        List<AgentRun> interrupted = runRepository.findByStatusIn(List.of("RUNNING"));
        if (interrupted.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AgentRun run : interrupted) {
            run.setStatus("INTERRUPTED");
            run.setErrorMessage("服务进程重启，运行已安全中断，可依据 checkpoint 重试");
            run.setUpdatedAt(now);
            run.setFinishedAt(now);
            runRepository.save(run);
            checkpoint(run.getGenerationId(), "RECOVERY_REQUIRED", Map.of(
                    "lastStage", run.getCurrentStage() == null ? "unknown" : run.getCurrentStage(),
                    "reason", "PROCESS_RESTART"
            ));
        }
        logger.warn("已将 {} 个悬空 Agent 运行标记为 INTERRUPTED", interrupted.size());
    }

    private void updateTerminal(String generationId,
                                String status,
                                String answer,
                                String error,
                                int promptTokens,
                                int completionTokens) {
        runRepository.findById(generationId).ifPresent(run -> {
            closeOpenSteps(generationId, status);
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

    private void closeOpenSteps(String generationId, String terminalStatus) {
        Map<String, AgentStep> latestByStepId = stepRepository.findByGenerationIdOrderByIdAsc(generationId).stream()
                .collect(Collectors.toMap(
                        AgentStep::getStepId,
                        Function.identity(),
                        (previous, current) -> current,
                        LinkedHashMap::new
                ));
        String closedStatus = "CANCELLED".equalsIgnoreCase(terminalStatus) ? "cancelled" : "failed";
        String detail = "cancelled".equals(closedStatus)
                ? "Agent 已停止，当前步骤安全关闭"
                : "Agent 运行终止，当前步骤已标记为失败";
        List<AgentStep> terminalSteps = latestByStepId.values().stream()
                .filter(step -> "running".equalsIgnoreCase(step.getStatus()))
                .map(step -> {
                    AgentStep terminal = new AgentStep();
                    terminal.setGenerationId(generationId);
                    terminal.setStepId(step.getStepId());
                    terminal.setStage(step.getStage());
                    terminal.setStatus(closedStatus);
                    terminal.setTitle(step.getTitle());
                    terminal.setDetail(detail);
                    terminal.setToolName(step.getToolName());
                    terminal.setMetadataJson(step.getMetadataJson());
                    terminal.setOccurredAt(LocalDateTime.now());
                    return terminal;
                })
                .toList();
        if (!terminalSteps.isEmpty()) {
            stepRepository.saveAll(terminalSteps);
        }
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

    private StepView toStepView(AgentRun run, AgentStep step) {
        boolean dangling = "running".equalsIgnoreCase(step.getStatus()) && !"RUNNING".equalsIgnoreCase(run.getStatus());
        String status = dangling
                ? ("CANCELLED".equalsIgnoreCase(run.getStatus()) ? "cancelled" : "failed")
                : step.getStatus();
        String rawDetail = dangling ? "Agent 运行终止，当前步骤已安全关闭" : step.getDetail();
        String detail = "failed".equalsIgnoreCase(status)
                ? errorSanitizer.auditMessage(rawDetail)
                : rawDetail;
        return new StepView(
                step.getStepId(),
                step.getStage(),
                status,
                step.getTitle(),
                detail,
                step.getToolName(),
                step.getOccurredAt(),
                readMetadata(step.getMetadataJson())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, LinkedHashMap.class);
        } catch (Exception exception) {
            return Map.of("serializationError", true);
        }
    }

    public record RunDetail(AgentRun run, List<AgentStep> steps, AgentCheckpoint latestCheckpoint) {
    }

    public record RetryCandidate(String generationId,
                                 String userId,
                                 String conversationId,
                                 String question,
                                 String lastStage,
                                 int attemptNumber) {
    }


    public record RunSummary(String generationId,
                             String conversationId,
                             String question,
                             String status,
                             String currentStage,
                             String errorMessage,
                             String retryOfGenerationId,
                             int attemptNumber,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt,
                             List<StepView> steps) {
    }

    public record StepView(String stepId,
                           String stage,
                           String status,
                           String title,
                           String detail,
                           String toolName,
                           LocalDateTime occurredAt,
                           Map<String, Object> metadata) {
    }
}

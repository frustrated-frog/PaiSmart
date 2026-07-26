package com.yizhaoqi.smartpai.evaluation;

import com.yizhaoqi.smartpai.model.AgentEvaluationCase;
import com.yizhaoqi.smartpai.model.AgentEvaluationDataset;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 对真实持久化运行做 pass@1 / pass^k 评测，客户端不能再直接提交 actual 与 passed。 */
@Service
public class AgentEvaluationRunner {

    private final AgentTraceProjector projector;
    private final AgentEvaluationService metricEngine;

    public AgentEvaluationRunner(AgentTraceProjector projector, AgentEvaluationService metricEngine) {
        this.projector = projector;
        this.metricEngine = metricEngine;
    }

    public RunnerReport run(AgentEvaluationDataset dataset, String userId, int requestedSampleCount) {
        if (dataset == null || dataset.cases().isEmpty()) {
            throw new IllegalArgumentException("Agent 评测数据集不能为空");
        }
        int sampleCount = Math.max(1, Math.min(10, requestedSampleCount));
        List<AgentEvaluationService.EvaluationAttempt> attempts = new ArrayList<>();
        for (AgentEvaluationCase evaluationCase : dataset.cases()) {
            if (evaluationCase.generationIds().size() < sampleCount) {
                throw new IllegalArgumentException("用例 " + evaluationCase.caseId() + " 的真实运行样本不足 " + sampleCount + " 个");
            }
            for (int index = 0; index < sampleCount; index++) {
                AgentTraceProjector.ProjectedTrace actual = projector.project(
                        evaluationCase.generationIds().get(index), userId);
                attempts.add(toAttempt(evaluationCase, actual, index + 1));
            }
        }
        return new RunnerReport(dataset.datasetId(), dataset.version(), sampleCount, metricEngine.evaluate(attempts));
    }

    private AgentEvaluationService.EvaluationAttempt toAttempt(AgentEvaluationCase expected,
                                                                AgentTraceProjector.ProjectedTrace actual,
                                                                int attemptIndex) {
        AgentEvaluationService.ExecutionSignals signals = new AgentEvaluationService.ExecutionSignals(
                expected.expectedIntent(), actual.actualIntent(),
                expected.expectedEvidenceStatus(), actual.actualEvidenceStatus(),
                expected.expectedConflict(), actual.actualConflict(),
                actual.recoveryAttempt(), actual.recoverySucceeded(),
                actual.reusedStepCount(), actual.duplicateSideEffectCount(), actual.writeActionCount(),
                actual.latencyMs(), actual.promptTokens(), actual.completionTokens()
        );
        boolean passed = expected.expectedClarification() == actual.actualClarification()
                && matches(expected.expectedIntent(), actual.actualIntent())
                && matches(expected.expectedEvidenceStatus(), actual.actualEvidenceStatus())
                && expected.expectedConflict() == actual.actualConflict()
                && isSubsequence(expected.expectedTrajectory(), actual.trajectory())
                && containsTools(expected.expectedTools(), actual.tools())
                && containsAll(actual.citationIds(), expected.expectedCitationIds())
                && containsAll(actual.supportedClaimIds(), expected.requiredClaimIds())
                && matches(expected.expectedTerminalReason(), actual.terminalReason());
        return new AgentEvaluationService.EvaluationAttempt(
                expected.caseId(), attemptIndex,
                expected.expectedClarification(), actual.actualClarification(),
                expected.expectedClarificationSlots(), actual.actualClarificationSlots(),
                expected.expectedTrajectory(), actual.trajectory(),
                expected.expectedTools(), actual.tools(),
                expected.expectedCitationIds(), actual.citationIds(),
                expected.requiredClaimIds(), actual.supportedClaimIds(),
                actual.duplicateActionCount(), actual.totalActionCount(),
                expected.expectedTerminalReason(), actual.terminalReason(), passed, signals
        );
    }

    private boolean containsTools(List<AgentEvaluationService.ToolCall> expected,
                                  List<AgentEvaluationService.ToolCall> actual) {
        Set<String> actualKeys = new HashSet<>();
        actual.forEach(tool -> actualKeys.add(normalize(tool.name()) + ':' + normalize(tool.argumentsFingerprint())));
        return expected.stream().allMatch(tool -> actualKeys.contains(
                normalize(tool.name()) + ':' + normalize(tool.argumentsFingerprint())));
    }

    private boolean isSubsequence(List<String> expected, List<String> actual) {
        int cursor = 0;
        for (String value : actual) {
            if (cursor < expected.size() && matches(expected.get(cursor), value)) {
                cursor++;
            }
        }
        return cursor == expected.size();
    }

    private boolean containsAll(List<String> actual, List<String> expected) {
        Set<String> values = actual.stream().map(this::normalize).collect(java.util.stream.Collectors.toSet());
        return expected.stream().map(this::normalize).allMatch(values::contains);
    }

    private boolean matches(String expected, String actual) {
        return normalize(expected).isEmpty() || normalize(expected).equals(normalize(actual));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record RunnerReport(String datasetId,
                               String datasetVersion,
                               int sampleCount,
                               AgentEvaluationService.EvaluationReport report) {
    }
}

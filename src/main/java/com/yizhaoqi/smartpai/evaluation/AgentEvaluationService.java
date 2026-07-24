package com.yizhaoqi.smartpai.evaluation;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 对结构化 Agent 轨迹做确定性离线评测，不再只依赖主观查看最终答案。 */
@Service
public class AgentEvaluationService {

    private final AgenticRagProperties properties;

    public AgentEvaluationService(AgenticRagProperties properties) {
        this.properties = properties;
    }

    public EvaluationReport evaluate(List<EvaluationAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            throw new IllegalArgumentException("Agent 评测用例不能为空");
        }
        validate(attempts);
        List<AttemptResult> results = attempts.stream().map(this::evaluateAttempt).toList();

        BinaryMetrics clarification = clarificationMetrics(attempts);
        double intentMacroF1 = categoricalMacroF1(attempts,
                item -> item.signals().expectedIntent(), item -> item.signals().actualIntent());
        double evidenceStatusMacroF1 = categoricalMacroF1(attempts,
                item -> item.signals().expectedEvidenceStatus(), item -> item.signals().actualEvidenceStatus());
        BinaryMetrics conflictDetection = binaryMetrics(attempts,
                item -> item.signals().expectedConflict(), item -> item.signals().actualConflict());
        double slotF1 = average(results.stream().map(AttemptResult::clarificationSlotF1).toList());
        double trajectory = average(results.stream().map(AttemptResult::trajectoryScore).toList());
        double invalidTransitionRate = average(results.stream().map(AttemptResult::invalidTransitionRate).toList());
        double toolSelection = average(results.stream().map(AttemptResult::toolSelectionF1).toList());
        double toolArguments = average(results.stream().map(AttemptResult::toolArgumentAccuracy).toList());
        double citationF1 = average(results.stream().map(AttemptResult::citationF1).toList());
        double claimCoverage = average(results.stream().map(AttemptResult::claimCoverage).toList());
        int duplicateActions = attempts.stream().mapToInt(item -> Math.max(0, item.duplicateActionCount())).sum();
        int totalActions = attempts.stream().mapToInt(item -> Math.max(0, item.totalActionCount())).sum();
        double duplicateRate = ratio(duplicateActions, totalActions);
        double terminalAccuracy = average(results.stream().map(item -> item.terminalReasonMatched() ? 1D : 0D).toList());
        double noProgressTerminationRate = attempts.stream()
                .filter(item -> "no_progress".equals(normalize(item.actualTerminalReason())))
                .count() / (double) attempts.size();
        List<EvaluationAttempt> recoveryAttempts = attempts.stream().filter(item -> item.signals().recoveryAttempt()).toList();
        double resumeSuccessRate = recoveryAttempts.isEmpty() ? 1D : recoveryAttempts.stream()
                .filter(item -> item.signals().recoverySucceeded()).count() / (double) recoveryAttempts.size();
        int reusedStepCount = attempts.stream().mapToInt(item -> Math.max(0, item.signals().reusedStepCount())).sum();
        int duplicateSideEffects = attempts.stream().mapToInt(item -> Math.max(0, item.signals().duplicateSideEffectCount())).sum();
        int writeActions = attempts.stream().mapToInt(item -> Math.max(0, item.signals().writeActionCount())).sum();
        double duplicateSideEffectRate = ratio(duplicateSideEffects, Math.max(writeActions, duplicateSideEffects));
        long p95LatencyMs = percentile95(attempts.stream().map(item -> Math.max(0L, item.signals().latencyMs())).sorted().toList());
        int totalTokens = attempts.stream().mapToInt(item -> Math.max(0, item.signals().promptTokens())
                + Math.max(0, item.signals().completionTokens())).sum();
        PassMetrics passMetrics = passMetrics(attempts);
        Gate gate = gate(clarification.f1(), intentMacroF1, evidenceStatusMacroF1, conflictDetection.f1(),
                trajectory, invalidTransitionRate, toolSelection, toolArguments, citationF1, claimCoverage,
                duplicateRate, terminalAccuracy, resumeSuccessRate, duplicateSideEffectRate, passMetrics.passPowerK());

        return new EvaluationReport(
                LocalDateTime.now().toString(),
                attempts.stream().map(EvaluationAttempt::caseId).distinct().count(),
                attempts.size(),
                clarification,
                round(intentMacroF1),
                round(evidenceStatusMacroF1),
                conflictDetection,
                round(slotF1),
                round(trajectory),
                round(invalidTransitionRate),
                round(toolSelection),
                round(toolArguments),
                round(citationF1),
                round(claimCoverage),
                round(duplicateRate),
                round(terminalAccuracy),
                round(noProgressTerminationRate),
                round(resumeSuccessRate),
                reusedStepCount,
                round(duplicateSideEffectRate),
                p95LatencyMs,
                totalTokens,
                passMetrics,
                gate,
                results
        );
    }

    private AttemptResult evaluateAttempt(EvaluationAttempt attempt) {
        double slotF1 = setF1(attempt.expectedClarificationSlots(), attempt.actualClarificationSlots());
        int lcs = longestCommonSubsequence(attempt.expectedTrajectory(), attempt.actualTrajectory());
        double trajectoryScore = f1(lcs, attempt.actualTrajectory().size(), attempt.expectedTrajectory().size());
        double invalidTransitionRate = invalidTransitionRate(attempt.expectedTrajectory(), attempt.actualTrajectory());
        Set<String> expectedTools = attempt.expectedTools().stream().map(item -> normalize(item.name())).collect(Collectors.toSet());
        Set<String> actualTools = attempt.actualTools().stream().map(item -> normalize(item.name())).collect(Collectors.toSet());
        double toolSelectionF1 = setF1(expectedTools, actualTools);
        double toolArgumentAccuracy = toolArgumentAccuracy(attempt.expectedTools(), attempt.actualTools());
        double citationF1 = setF1(attempt.expectedCitationIds(), attempt.actualCitationIds());
        double claimCoverage = coverage(attempt.requiredClaimIds(), attempt.supportedClaimIds());
        boolean terminalMatched = normalize(attempt.expectedTerminalReason())
                .equals(normalize(attempt.actualTerminalReason()));
        return new AttemptResult(
                attempt.caseId(),
                attempt.attemptIndex(),
                round(slotF1),
                round(trajectoryScore),
                round(invalidTransitionRate),
                round(toolSelectionF1),
                round(toolArgumentAccuracy),
                round(citationF1),
                round(claimCoverage),
                terminalMatched,
                attempt.passed()
        );
    }

    private BinaryMetrics clarificationMetrics(List<EvaluationAttempt> attempts) {
        return binaryMetrics(attempts, EvaluationAttempt::expectedClarification, EvaluationAttempt::actualClarification);
    }

    private BinaryMetrics binaryMetrics(List<EvaluationAttempt> attempts,
                                        java.util.function.Predicate<EvaluationAttempt> expected,
                                        java.util.function.Predicate<EvaluationAttempt> actual) {
        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        for (EvaluationAttempt attempt : attempts) {
            if (expected.test(attempt) && actual.test(attempt)) {
                truePositive++;
            } else if (!expected.test(attempt) && actual.test(attempt)) {
                falsePositive++;
            } else if (expected.test(attempt)) {
                falseNegative++;
            }
        }
        double precision = truePositive + falsePositive == 0 ? 1D : ratio(truePositive, truePositive + falsePositive);
        double recall = truePositive + falseNegative == 0 ? 1D : ratio(truePositive, truePositive + falseNegative);
        return new BinaryMetrics(round(precision), round(recall), round(harmonicMean(precision, recall)),
                truePositive, falsePositive, falseNegative);
    }

    private PassMetrics passMetrics(List<EvaluationAttempt> attempts) {
        Map<String, List<EvaluationAttempt>> grouped = attempts.stream()
                .collect(Collectors.groupingBy(EvaluationAttempt::caseId, LinkedHashMap::new, Collectors.toList()));
        long passAtOneCount = grouped.values().stream()
                .filter(values -> values.stream().min(java.util.Comparator.comparingInt(EvaluationAttempt::attemptIndex))
                        .map(EvaluationAttempt::passed).orElse(false))
                .count();
        long passPowerKCount = grouped.values().stream()
                .filter(values -> values.stream().allMatch(EvaluationAttempt::passed))
                .count();
        int observedK = grouped.values().stream().mapToInt(List::size).max().orElse(1);
        return new PassMetrics(observedK, round(ratio(passAtOneCount, grouped.size())),
                round(ratio(passPowerKCount, grouped.size())));
    }

    private Gate gate(double clarificationF1,
                      double intentMacroF1,
                      double evidenceStatusMacroF1,
                      double conflictDetectionF1,
                      double trajectory,
                      double invalidTransitionRate,
                      double toolSelection,
                      double toolArguments,
                      double citationF1,
                      double claimCoverage,
                      double duplicateRate,
                      double terminalAccuracy,
                      double resumeSuccessRate,
                      double duplicateSideEffectRate,
                      double passPowerK) {
        AgenticRagProperties.Evaluation thresholds = properties.getEvaluation();
        Map<String, GateCheck> checks = new LinkedHashMap<>();
        checks.put("clarificationF1", minimum(clarificationF1, thresholds.getMinClarificationF1()));
        checks.put("intentMacroF1", minimum(intentMacroF1, thresholds.getMinIntentMacroF1()));
        checks.put("evidenceStatusMacroF1", minimum(evidenceStatusMacroF1, thresholds.getMinEvidenceStatusMacroF1()));
        checks.put("conflictDetectionF1", minimum(conflictDetectionF1, thresholds.getMinConflictDetectionF1()));
        checks.put("trajectoryScore", minimum(trajectory, thresholds.getMinTrajectoryScore()));
        checks.put("invalidTransitionRate", maximum(invalidTransitionRate, thresholds.getMaxInvalidTransitionRate()));
        checks.put("toolSelectionF1", minimum(toolSelection, thresholds.getMinToolSelectionF1()));
        checks.put("toolArgumentAccuracy", minimum(toolArguments, thresholds.getMinToolArgumentAccuracy()));
        checks.put("citationF1", minimum(citationF1, thresholds.getMinCitationF1()));
        checks.put("claimCoverage", minimum(claimCoverage, thresholds.getMinClaimCoverage()));
        checks.put("duplicateActionRate", maximum(duplicateRate, thresholds.getMaxDuplicateActionRate()));
        checks.put("terminalReasonAccuracy", minimum(terminalAccuracy, thresholds.getMinTerminalReasonAccuracy()));
        checks.put("resumeSuccessRate", minimum(resumeSuccessRate, thresholds.getMinResumeSuccessRate()));
        checks.put("duplicateSideEffectRate", maximum(duplicateSideEffectRate, thresholds.getMaxDuplicateSideEffectRate()));
        checks.put("passPowerK", minimum(passPowerK, thresholds.getMinPassPowerK()));
        return new Gate(checks.values().stream().allMatch(GateCheck::passed), checks);
    }

    private double toolArgumentAccuracy(List<ToolCall> expected, List<ToolCall> actual) {
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1D : 0D;
        }
        Map<String, Set<String>> actualFingerprints = actual.stream().collect(Collectors.groupingBy(
                item -> normalize(item.name()),
                Collectors.mapping(item -> normalize(item.argumentsFingerprint()), Collectors.toSet())
        ));
        long matched = expected.stream().filter(item -> actualFingerprints
                .getOrDefault(normalize(item.name()), Set.of())
                .contains(normalize(item.argumentsFingerprint()))).count();
        return ratio(matched, expected.size());
    }

    private double categoricalMacroF1(List<EvaluationAttempt> attempts,
                                      java.util.function.Function<EvaluationAttempt, String> expected,
                                      java.util.function.Function<EvaluationAttempt, String> actual) {
        Set<String> labels = attempts.stream()
                .flatMap(item -> java.util.stream.Stream.of(normalize(expected.apply(item)), normalize(actual.apply(item))))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        if (labels.isEmpty()) {
            return 1D;
        }
        return labels.stream().mapToDouble(label -> {
            long truePositive = attempts.stream().filter(item -> label.equals(normalize(expected.apply(item)))
                    && label.equals(normalize(actual.apply(item)))).count();
            long predicted = attempts.stream().filter(item -> label.equals(normalize(actual.apply(item)))).count();
            long expectedCount = attempts.stream().filter(item -> label.equals(normalize(expected.apply(item)))).count();
            return f1((int) truePositive, (int) predicted, (int) expectedCount);
        }).average().orElse(0D);
    }

    private int longestCommonSubsequence(List<String> expected, List<String> actual) {
        int[][] lengths = new int[expected.size() + 1][actual.size() + 1];
        for (int left = 1; left <= expected.size(); left++) {
            for (int right = 1; right <= actual.size(); right++) {
                lengths[left][right] = normalize(expected.get(left - 1)).equals(normalize(actual.get(right - 1)))
                        ? lengths[left - 1][right - 1] + 1
                        : Math.max(lengths[left - 1][right], lengths[left][right - 1]);
            }
        }
        return lengths[expected.size()][actual.size()];
    }

    private double invalidTransitionRate(List<String> expected, List<String> actual) {
        if (actual.size() < 2) {
            return 0D;
        }
        Set<String> expectedTransitions = new HashSet<>();
        for (int index = 1; index < expected.size(); index++) {
            expectedTransitions.add(normalize(expected.get(index - 1)) + "->" + normalize(expected.get(index)));
        }
        int invalid = 0;
        for (int index = 1; index < actual.size(); index++) {
            String transition = normalize(actual.get(index - 1)) + "->" + normalize(actual.get(index));
            if (!expectedTransitions.contains(transition)) {
                invalid++;
            }
        }
        return ratio(invalid, actual.size() - 1L);
    }

    private double setF1(List<String> expected, List<String> actual) {
        return setF1(new HashSet<>(expected), new HashSet<>(actual));
    }

    private double setF1(Set<String> expected, Set<String> actual) {
        Set<String> normalizedExpected = expected.stream().map(this::normalize).collect(Collectors.toSet());
        Set<String> normalizedActual = actual.stream().map(this::normalize).collect(Collectors.toSet());
        Set<String> intersection = new HashSet<>(normalizedExpected);
        intersection.retainAll(normalizedActual);
        return f1(intersection.size(), normalizedActual.size(), normalizedExpected.size());
    }

    private double f1(int matched, int predicted, int expected) {
        if (predicted == 0 && expected == 0) {
            return 1D;
        }
        double precision = predicted == 0 ? 0D : ratio(matched, predicted);
        double recall = expected == 0 ? 0D : ratio(matched, expected);
        return harmonicMean(precision, recall);
    }

    private double coverage(List<String> required, List<String> supported) {
        if (required.isEmpty()) {
            return 1D;
        }
        Set<String> supportedSet = supported.stream().map(this::normalize).collect(Collectors.toSet());
        long matched = required.stream().map(this::normalize).distinct().filter(supportedSet::contains).count();
        return ratio(matched, required.stream().map(this::normalize).distinct().count());
    }

    private void validate(List<EvaluationAttempt> attempts) {
        Set<String> keys = new HashSet<>();
        for (EvaluationAttempt attempt : attempts) {
            if (attempt == null || normalize(attempt.caseId()).isEmpty()) {
                throw new IllegalArgumentException("caseId 不能为空");
            }
            if (attempt.attemptIndex() < 1) {
                throw new IllegalArgumentException("attemptIndex 必须从 1 开始");
            }
            String key = attempt.caseId() + ':' + attempt.attemptIndex();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("同一 caseId 下 attemptIndex 不能重复: " + key);
            }
        }
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0D : (double) numerator / denominator;
    }

    private double harmonicMean(double left, double right) {
        return left + right == 0D ? 0D : 2D * left * right / (left + right);
    }

    private double round(double value) {
        return Math.round(value * 10_000D) / 10_000D;
    }

    private long percentile95(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(values.size() * 0.95D) - 1);
        return values.get(Math.min(index, values.size() - 1));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private GateCheck minimum(double actual, double threshold) {
        return new GateCheck(actual >= threshold, round(actual), threshold, ">=");
    }

    private GateCheck maximum(double actual, double threshold) {
        return new GateCheck(actual <= threshold, round(actual), threshold, "<=");
    }

    public record EvaluationAttempt(String caseId,
                                    int attemptIndex,
                                    boolean expectedClarification,
                                    boolean actualClarification,
                                    List<String> expectedClarificationSlots,
                                    List<String> actualClarificationSlots,
                                    List<String> expectedTrajectory,
                                    List<String> actualTrajectory,
                                    List<ToolCall> expectedTools,
                                    List<ToolCall> actualTools,
                                    List<String> expectedCitationIds,
                                    List<String> actualCitationIds,
                                    List<String> requiredClaimIds,
                                    List<String> supportedClaimIds,
                                    int duplicateActionCount,
                                    int totalActionCount,
                                    String expectedTerminalReason,
                                    String actualTerminalReason,
                                    boolean passed,
                                    ExecutionSignals signals) {
        public EvaluationAttempt {
            expectedClarificationSlots = safe(expectedClarificationSlots);
            actualClarificationSlots = safe(actualClarificationSlots);
            expectedTrajectory = safe(expectedTrajectory);
            actualTrajectory = safe(actualTrajectory);
            expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
            actualTools = actualTools == null ? List.of() : List.copyOf(actualTools);
            expectedCitationIds = safe(expectedCitationIds);
            actualCitationIds = safe(actualCitationIds);
            requiredClaimIds = safe(requiredClaimIds);
            supportedClaimIds = safe(supportedClaimIds);
            signals = signals == null ? ExecutionSignals.empty() : signals;
        }

        private static List<String> safe(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    public record ToolCall(String name, String argumentsFingerprint) {
    }

    public record ExecutionSignals(String expectedIntent,
                                   String actualIntent,
                                   String expectedEvidenceStatus,
                                   String actualEvidenceStatus,
                                   boolean expectedConflict,
                                   boolean actualConflict,
                                   boolean recoveryAttempt,
                                   boolean recoverySucceeded,
                                   int reusedStepCount,
                                   int duplicateSideEffectCount,
                                   int writeActionCount,
                                   long latencyMs,
                                   int promptTokens,
                                   int completionTokens) {
        public static ExecutionSignals empty() {
            return new ExecutionSignals("", "", "", "", false, false,
                    false, false, 0, 0, 0, 0L, 0, 0);
        }
    }

    public record AttemptResult(String caseId,
                                int attemptIndex,
                                double clarificationSlotF1,
                                double trajectoryScore,
                                double invalidTransitionRate,
                                double toolSelectionF1,
                                double toolArgumentAccuracy,
                                double citationF1,
                                double claimCoverage,
                                boolean terminalReasonMatched,
                                boolean passed) {
    }

    public record BinaryMetrics(double precision,
                                double recall,
                                double f1,
                                int truePositive,
                                int falsePositive,
                                int falseNegative) {
    }

    /** passPowerK 表示同一用例的 k 次采样全部成功，即文档中的 pass^k。 */
    public record PassMetrics(int observedK, double passAtOne, double passPowerK) {
    }

    public record Gate(boolean passed, Map<String, GateCheck> checks) {
    }

    public record GateCheck(boolean passed, double actual, double threshold, String operator) {
    }

    public record EvaluationReport(String evaluatedAt,
                                   long caseCount,
                                   int attemptCount,
                                   BinaryMetrics clarification,
                                   double intentMacroF1,
                                   double evidenceStatusMacroF1,
                                   BinaryMetrics conflictDetection,
                                   double clarificationSlotF1,
                                   double trajectoryScore,
                                   double invalidTransitionRate,
                                   double toolSelectionF1,
                                   double toolArgumentAccuracy,
                                   double citationF1,
                                   double claimCoverage,
                                   double duplicateActionRate,
                                   double terminalReasonAccuracy,
                                   double noProgressTerminationRate,
                                   double resumeSuccessRate,
                                   int reusedStepCount,
                                   double duplicateSideEffectRate,
                                   long p95LatencyMs,
                                   int totalTokens,
                                   PassMetrics passMetrics,
                                   Gate gate,
                                   List<AttemptResult> attempts) {
    }
}

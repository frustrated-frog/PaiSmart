package com.yizhaoqi.smartpai.evaluation;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEvaluationServiceTest {

    private final AgentEvaluationService service = new AgentEvaluationService(new AgenticRagProperties());

    @Test
    void evaluatesTrajectoryToolsEvidenceTerminationAndPassAtK() {
        AgentEvaluationService.EvaluationReport report = service.evaluate(List.of(
                attempt("case-a", 1, true, true,
                        List.of("scope"), List.of("scope"),
                        List.of("plan", "search", "verify", "answer"),
                        List.of("plan", "search", "verify", "answer"),
                        List.of(tool("search_knowledge", "fp-a")),
                        List.of(tool("search_knowledge", "fp-a")),
                        List.of("doc-1"), List.of("doc-1"),
                        List.of("claim-1"), List.of("claim-1"),
                        0, 1, "ANSWERED", "ANSWERED", true, signals(false, true, 0, 0, 0, 800)),
                attempt("case-a", 2, true, false,
                        List.of("scope"), List.of(),
                        List.of("plan", "search", "verify", "answer"),
                        List.of("plan", "search", "answer"),
                        List.of(tool("search_knowledge", "fp-a")),
                        List.of(tool("search_knowledge", "wrong")),
                        List.of("doc-1"), List.of(),
                        List.of("claim-1"), List.of(),
                        1, 2, "ANSWERED", "NO_PROGRESS", false, signals(true, false, 1, 1, 1, 1500)),
                attempt("case-b", 1, false, false,
                        List.of(), List.of(),
                        List.of("plan", "answer"), List.of("plan", "answer"),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        0, 0, "ANSWERED", "ANSWERED", true, signals(false, true, 0, 0, 0, 500))
        ));

        assertThat(report.caseCount()).isEqualTo(2);
        assertThat(report.attemptCount()).isEqualTo(3);
        assertThat(report.clarification().f1()).isEqualTo(0.6667D);
        assertThat(report.duplicateActionRate()).isEqualTo(0.3333D);
        assertThat(report.passMetrics().observedK()).isEqualTo(2);
        assertThat(report.passMetrics().passAtOne()).isEqualTo(1D);
        assertThat(report.passMetrics().passPowerK()).isEqualTo(0.5D);
        assertThat(report.resumeSuccessRate()).isEqualTo(0D);
        assertThat(report.reusedStepCount()).isEqualTo(1);
        assertThat(report.duplicateSideEffectRate()).isEqualTo(1D);
        assertThat(report.p95LatencyMs()).isEqualTo(1500L);
        assertThat(report.gate().passed()).isFalse();
        assertThat(report.gate().checks()).containsKeys(
                "trajectoryScore", "invalidTransitionRate", "toolArgumentAccuracy", "claimCoverage", "terminalReasonAccuracy"
        );
    }

    @Test
    void rejectsDuplicateAttemptIdentity() {
        AgentEvaluationService.EvaluationAttempt attempt = attempt(
                "same", 1, false, false,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), 0, 0, "ANSWERED", "ANSWERED", true,
                AgentEvaluationService.ExecutionSignals.empty()
        );

        assertThatThrownBy(() -> service.evaluate(List.of(attempt, attempt)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptIndex 不能重复");
    }

    private AgentEvaluationService.ToolCall tool(String name, String fingerprint) {
        return new AgentEvaluationService.ToolCall(name, fingerprint);
    }

    private AgentEvaluationService.EvaluationAttempt attempt(
            String caseId,
            int attemptIndex,
            boolean expectedClarification,
            boolean actualClarification,
            List<String> expectedSlots,
            List<String> actualSlots,
            List<String> expectedTrajectory,
            List<String> actualTrajectory,
            List<AgentEvaluationService.ToolCall> expectedTools,
            List<AgentEvaluationService.ToolCall> actualTools,
            List<String> expectedCitationIds,
            List<String> actualCitationIds,
            List<String> requiredClaimIds,
            List<String> supportedClaimIds,
            int duplicateActionCount,
            int totalActionCount,
            String expectedTerminalReason,
            String actualTerminalReason,
            boolean passed,
            AgentEvaluationService.ExecutionSignals signals) {
        return new AgentEvaluationService.EvaluationAttempt(
                caseId, attemptIndex, expectedClarification, actualClarification,
                expectedSlots, actualSlots, expectedTrajectory, actualTrajectory,
                expectedTools, actualTools, expectedCitationIds, actualCitationIds,
                requiredClaimIds, supportedClaimIds, duplicateActionCount, totalActionCount,
                expectedTerminalReason, actualTerminalReason, passed, signals
        );
    }

    private AgentEvaluationService.ExecutionSignals signals(boolean recoveryAttempt,
                                                             boolean recoverySucceeded,
                                                             int reusedSteps,
                                                             int duplicateSideEffects,
                                                             int writeActions,
                                                             long latencyMs) {
        return new AgentEvaluationService.ExecutionSignals(
                "KNOWLEDGE_QA", "KNOWLEDGE_QA", "SUFFICIENT", "SUFFICIENT",
                false, false, recoveryAttempt, recoverySucceeded, reusedSteps,
                duplicateSideEffects, writeActions, latencyMs, 100, 20
        );
    }
}

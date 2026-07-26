package com.yizhaoqi.smartpai.evaluation;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.model.AgentEvaluationCase;
import com.yizhaoqi.smartpai.model.AgentEvaluationDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEvaluationRunnerTest {

    @Mock AgentTraceProjector projector;

    @Test
    void samplesPersistedRunsAndDerivesPassResultServerSide() {
        AgentEvaluationCase evaluationCase = new AgentEvaluationCase(
                "case-1", "事实问答", false, List.of(),
                "FACTUAL", "SUFFICIENT", false,
                List.of("understanding", "retrieval"),
                List.of(new AgentEvaluationService.ToolCall("search_knowledge", "fp")),
                List.of("1"), List.of(), "ANSWERED", List.of("run-1", "run-2")
        );
        AgentEvaluationDataset dataset = new AgentEvaluationDataset("core", "v1", List.of(evaluationCase));
        AgentTraceProjector.ProjectedTrace passing = projected("FACTUAL", "ANSWERED");
        AgentTraceProjector.ProjectedTrace failing = projected("CHAT", "ANSWERED");
        when(projector.project("run-1", "7")).thenReturn(passing);
        when(projector.project("run-2", "7")).thenReturn(failing);

        AgentEvaluationRunner.RunnerReport report = new AgentEvaluationRunner(
                projector, new AgentEvaluationService(new AgenticRagProperties()))
                .run(dataset, "7", 2);

        assertThat(report.datasetId()).isEqualTo("core");
        assertThat(report.report().attempts()).extracting(AgentEvaluationService.AttemptResult::attemptIndex)
                .containsExactly(1, 2);
        assertThat(report.report().attempts()).extracting(AgentEvaluationService.AttemptResult::passed)
                .containsExactly(true, false);
    }

    private AgentTraceProjector.ProjectedTrace projected(String intent, String terminalReason) {
        return new AgentTraceProjector.ProjectedTrace(
                false, List.of(), List.of("understanding", "retrieval"),
                List.of(new AgentEvaluationService.ToolCall("search_knowledge", "fp")),
                List.of("1"), List.of(), 0, 1, terminalReason,
                intent, "SUFFICIENT", false, false, false,
                0, 0, 0, 100, 20, 10
        );
    }
}

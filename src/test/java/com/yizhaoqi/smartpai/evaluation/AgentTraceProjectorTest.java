package com.yizhaoqi.smartpai.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentRun;
import com.yizhaoqi.smartpai.model.AgentStep;
import com.yizhaoqi.smartpai.model.AgentToolCall;
import com.yizhaoqi.smartpai.repository.AgentRunRepository;
import com.yizhaoqi.smartpai.repository.AgentStepRepository;
import com.yizhaoqi.smartpai.repository.AgentToolCallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTraceProjectorTest {

    @Mock AgentRunRepository runRepository;
    @Mock AgentStepRepository stepRepository;
    @Mock AgentToolCallRepository toolCallRepository;

    @Test
    void projectsEvaluationSignalsOnlyFromPersistedFacts() {
        AgentRun run = new AgentRun();
        run.setGenerationId("run-1");
        run.setUserId("7");
        run.setStatus("COMPLETED");
        run.setTerminalReason("ANSWERED");
        run.setRetryOfGenerationId("source");
        run.setPromptTokens(120);
        run.setCompletionTokens(30);
        run.setCreatedAt(LocalDateTime.parse("2026-07-26T10:00:00"));
        run.setFinishedAt(LocalDateTime.parse("2026-07-26T10:00:02"));
        run.setAnswer("结论见 [1]");

        AgentStep planning = step("planning", "understanding", "{\"retrievalTrace\":{\"queryPlan\":{\"intent\":\"FACTUAL\"}}}");
        AgentStep retrieval = step("retrieval", "retrieval", "{\"evidenceAssessment\":{\"status\":\"SUFFICIENT\",\"conflicts\":[]}}");
        AgentToolCall tool = new AgentToolCall();
        tool.setToolName("search_knowledge");
        tool.setActionFingerprint("fp-1");
        tool.setStatus("SUCCESS");
        tool.setToolEffect("READ");

        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        when(stepRepository.findByGenerationIdOrderByIdAsc("run-1")).thenReturn(List.of(planning, retrieval));
        when(toolCallRepository.findByGenerationIdOrderByIdAsc("run-1")).thenReturn(List.of(tool));

        AgentTraceProjector.ProjectedTrace trace = new AgentTraceProjector(
                runRepository, stepRepository, toolCallRepository, new ObjectMapper()).project("run-1", "7");

        assertThat(trace.actualIntent()).isEqualTo("FACTUAL");
        assertThat(trace.actualEvidenceStatus()).isEqualTo("SUFFICIENT");
        assertThat(trace.trajectory()).containsExactly("understanding", "retrieval");
        assertThat(trace.tools()).extracting(AgentEvaluationService.ToolCall::name)
                .containsExactly("search_knowledge");
        assertThat(trace.citationIds()).containsExactly("1");
        assertThat(trace.recoveryAttempt()).isTrue();
        assertThat(trace.recoverySucceeded()).isTrue();
        assertThat(trace.latencyMs()).isEqualTo(2000L);
        assertThat(trace.promptTokens()).isEqualTo(120);
    }

    private AgentStep step(String id, String stage, String metadata) {
        AgentStep step = new AgentStep();
        step.setStepId(id);
        step.setStage(stage);
        step.setStatus("completed");
        step.setMetadataJson(metadata);
        return step;
    }
}

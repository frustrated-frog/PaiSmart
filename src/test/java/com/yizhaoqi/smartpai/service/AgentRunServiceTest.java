package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentRun;
import com.yizhaoqi.smartpai.model.AgentStep;
import com.yizhaoqi.smartpai.repository.AgentCheckpointRepository;
import com.yizhaoqi.smartpai.repository.AgentRunRepository;
import com.yizhaoqi.smartpai.repository.AgentStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {

    @Mock
    private AgentRunRepository runRepository;
    @Mock
    private AgentStepRepository stepRepository;
    @Mock
    private AgentCheckpointRepository checkpointRepository;

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        service = new AgentRunService(
                runRepository,
                stepRepository,
                checkpointRepository,
                new ObjectMapper(),
                new AgentErrorSanitizer()
        );
    }

    @Test
    void shouldCreateRetryWithIndependentRunLineage() {
        AgentRunService.RetryCandidate source = new AgentRunService.RetryCandidate(
                "source-run", "7", "conversation-1", "原问题", "retrieval", 2
        );
        when(runRepository.existsById("retry-run")).thenReturn(false);

        service.startRetry("retry-run", source);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runRepository).save(captor.capture());
        AgentRun retry = captor.getValue();
        assertThat(retry.getGenerationId()).isEqualTo("retry-run");
        assertThat(retry.getRetryOfGenerationId()).isEqualTo("source-run");
        assertThat(retry.getAttemptNumber()).isEqualTo(3);
        assertThat(retry.getStatus()).isEqualTo("RUNNING");
        verify(checkpointRepository).save(any());
    }

    @Test
    void shouldOnlyAllowRetryForTerminalRecoverableStatus() {
        AgentRun completed = run("run-1", "7", "COMPLETED");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.getRetryCandidate("run-1", "7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("失败、中断或取消");
    }

    @Test
    void shouldRejectRetryAcrossUserBoundary() {
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run("run-1", "8", "FAILED")));

        assertThatThrownBy(() -> service.getRetryCandidate("run-1", "7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agent 运行记录不存在");
    }

    @Test
    void shouldRestoreFailedRunWithSanitizedTimeline() {
        AgentRun failed = run("run-1", "7", "FAILED");
        failed.setErrorMessage("401 Unauthorized from POST https://provider.example/v1/chat");
        AgentStep step = new AgentStep();
        step.setStepId("reasoning-1");
        step.setStage("reasoning");
        step.setStatus("failed");
        step.setTitle("Agent 执行中断");
        step.setDetail("401 Unauthorized from POST https://provider.example/v1/chat");
        step.setMetadataJson("{}");
        when(runRepository.findTop50ByUserIdAndConversationIdOrderByCreatedAtAsc("7", "conversation-1"))
                .thenReturn(List.of(failed));
        when(stepRepository.findByGenerationIdOrderByIdAsc("run-1")).thenReturn(List.of(step));

        List<AgentRunService.RunSummary> summaries = service.listRecoverable("7", "conversation-1");

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).errorMessage()).doesNotContain("https://", "provider.example");
        assertThat(summaries.get(0).steps().get(0).detail()).contains("认证失败");
    }

    private AgentRun run(String generationId, String userId, String status) {
        AgentRun run = new AgentRun();
        run.setGenerationId(generationId);
        run.setUserId(userId);
        run.setConversationId("conversation-1");
        run.setQuestion("问题");
        run.setStatus(status);
        run.setAttemptNumber(1);
        return run;
    }
}

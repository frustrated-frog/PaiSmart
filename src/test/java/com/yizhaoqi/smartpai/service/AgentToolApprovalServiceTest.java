package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentRun;
import com.yizhaoqi.smartpai.model.AgentToolCall;
import com.yizhaoqi.smartpai.repository.AgentRunRepository;
import com.yizhaoqi.smartpai.repository.AgentToolCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolApprovalServiceTest {

    private AgentRunRepository runRepository;
    private AgentToolCallRepository toolRepository;
    private AgentToolApprovalService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(AgentRunRepository.class);
        toolRepository = mock(AgentToolCallRepository.class);
        service = new AgentToolApprovalService(runRepository, toolRepository);
    }

    @Test
    void ownerCanApproveWaitingToolCall() {
        AgentRun run = run("run-1", "7", "WAITING_APPROVAL");
        AgentToolCall toolCall = toolCall(12L, "run-1", "WAITING_APPROVAL");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        when(toolRepository.findById(12L)).thenReturn(Optional.of(toolCall));

        AgentToolApprovalService.ApprovalResult result = service.decide(
                "7", "run-1", 12L, AgentToolApprovalService.ApprovalDecision.APPROVE);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(toolCall.getStatus()).isEqualTo("APPROVED");
        verify(toolRepository).save(toolCall);
    }

    @Test
    void rejectsApprovalFromDifferentUser() {
        when(runRepository.findById("run-1"))
                .thenReturn(Optional.of(run("run-1", "7", "WAITING_APPROVAL")));

        assertThatThrownBy(() -> service.decide(
                "8", "run-1", 12L, AgentToolApprovalService.ApprovalDecision.APPROVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void sameDecisionIsIdempotentButOppositeDecisionConflicts() {
        AgentRun run = run("run-1", "7", "WAITING_APPROVAL");
        AgentToolCall toolCall = toolCall(12L, "run-1", "APPROVED");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        when(toolRepository.findById(12L)).thenReturn(Optional.of(toolCall));

        AgentToolApprovalService.ApprovalResult same = service.decide(
                "7", "run-1", 12L, AgentToolApprovalService.ApprovalDecision.APPROVE);

        assertThat(same.status()).isEqualTo("APPROVED");
        assertThat(same.idempotent()).isTrue();
        assertThatThrownBy(() -> service.decide(
                "7", "run-1", 12L, AgentToolApprovalService.ApprovalDecision.REJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已经完成");
    }

    private AgentRun run(String generationId, String userId, String status) {
        AgentRun run = new AgentRun();
        run.setGenerationId(generationId);
        run.setUserId(userId);
        run.setStatus(status);
        return run;
    }

    private AgentToolCall toolCall(long id, String generationId, String status) {
        AgentToolCall call = new AgentToolCall();
        call.setId(id);
        call.setGenerationId(generationId);
        call.setToolName("submit_feedback");
        call.setStatus(status);
        return call;
    }
}

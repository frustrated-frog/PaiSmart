package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentToolCall;
import com.yizhaoqi.smartpai.repository.AgentToolCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolLedgerServiceTest {

    @Mock
    private AgentToolCallRepository repository;

    private AgentToolLedgerService service;
    private final AtomicLong ids = new AtomicLong(20);

    @BeforeEach
    void setUp() {
        service = new AgentToolLedgerService(repository, new ObjectMapper());
        when(repository.save(any(AgentToolCall.class))).thenAnswer(invocation -> {
            AgentToolCall call = invocation.getArgument(0);
            if (call.getId() == null) {
                call.setId(ids.incrementAndGet());
            }
            return call;
        });
    }

    @Test
    void replaysSuccessfulReadToolAcrossRetryLineage() {
        AgentToolCall source = existing("source", "fp-read", "SUCCESS",
                AgentToolRegistry.ReplayPolicy.REPLAY_SAFE);
        source.setResultContent("已检索到证据");
        source.setResultDataJson("{\"results\":[{\"fileMd5\":\"abc\",\"chunkId\":2,\"textContent\":\"证据\",\"score\":0.8}]}");
        when(repository.findTopByGenerationIdAndActionFingerprintOrderByIdDesc("retry", "fp-read"))
                .thenReturn(Optional.empty());
        when(repository.findTopByGenerationIdAndActionFingerprintOrderByIdDesc("source", "fp-read"))
                .thenReturn(Optional.of(source));

        AgentToolLedgerService.PreparedToolCall prepared = service.prepare(
                "retry", "source", "call-2", "search_knowledge", "fp-read",
                Map.of("query", "RRF"), readPolicy()
        );

        assertThat(prepared.disposition()).isEqualTo(AgentToolLedgerService.Disposition.REUSE);
        assertThat(prepared.replayedResult().content()).isEqualTo("已检索到证据");
        assertThat(prepared.replayedResult().data().get("results")).asList().hasSize(1);
        assertThat(prepared.ticket().getStatus()).isEqualTo("REUSED");
        assertThat(prepared.ticket().getReusedFromToolCallId()).isEqualTo(source.getId());
    }

    @Test
    void blocksAtMostOnceWriteWhenPreviousOutcomeIsUnknown() {
        AgentToolCall source = existing("source", "fp-write", "UNKNOWN",
                AgentToolRegistry.ReplayPolicy.AT_MOST_ONCE);
        when(repository.findTopByGenerationIdAndActionFingerprintOrderByIdDesc("retry", "fp-write"))
                .thenReturn(Optional.empty());
        when(repository.findTopByGenerationIdAndActionFingerprintOrderByIdDesc("source", "fp-write"))
                .thenReturn(Optional.of(source));

        AgentToolLedgerService.PreparedToolCall prepared = service.prepare(
                "retry", "source", "call-2", "submit_feedback", "fp-write",
                Map.of("rating", "good"), writePolicy()
        );

        assertThat(prepared.disposition()).isEqualTo(AgentToolLedgerService.Disposition.BLOCK);
        assertThat(prepared.ticket().getStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(prepared.message()).contains("人工确认");
    }

    @Test
    void marksFailedAtMostOnceCallAsUnknown() {
        when(repository.findTopByGenerationIdAndActionFingerprintOrderByIdDesc("run", "fp"))
                .thenReturn(Optional.empty());
        AgentToolLedgerService.PreparedToolCall prepared = service.prepare(
                "run", null, "call", "submit_feedback", "fp",
                Map.of("rating", "bad"), writePolicy()
        );

        service.fail(prepared.ticket(), new RuntimeException("connection reset"));

        assertThat(prepared.ticket().getStatus()).isEqualTo("UNKNOWN");
    }

    private AgentToolCall existing(String generationId,
                                   String fingerprint,
                                   String status,
                                   AgentToolRegistry.ReplayPolicy replayPolicy) {
        AgentToolCall call = new AgentToolCall();
        call.setId(ids.incrementAndGet());
        call.setGenerationId(generationId);
        call.setToolName("tool");
        call.setActionFingerprint(fingerprint);
        call.setIdempotencyKey(generationId + fingerprint);
        call.setRiskLevel(AgentToolExecutionGuard.RiskLevel.READ_ONLY.name());
        call.setToolEffect(AgentToolRegistry.ToolEffect.READ.name());
        call.setReplayPolicy(replayPolicy.name());
        call.setStatus(status);
        call.setArgumentsJson("{}");
        call.setStartedAt(LocalDateTime.now());
        return call;
    }

    private AgentToolRegistry.ToolPolicy readPolicy() {
        return new AgentToolRegistry.ToolPolicy(
                AgentToolExecutionGuard.RiskLevel.READ_ONLY,
                AgentToolRegistry.ToolEffect.READ,
                AgentToolRegistry.ConcurrencyPolicy.PARALLEL_SAFE,
                AgentToolRegistry.ReplayPolicy.REPLAY_SAFE
        );
    }

    private AgentToolRegistry.ToolPolicy writePolicy() {
        return new AgentToolRegistry.ToolPolicy(
                AgentToolExecutionGuard.RiskLevel.WRITE_USER_SCOPE,
                AgentToolRegistry.ToolEffect.WRITE,
                AgentToolRegistry.ConcurrencyPolicy.SERIAL_PER_USER,
                AgentToolRegistry.ReplayPolicy.AT_MOST_ONCE
        );
    }
}

package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentCheckpoint;
import com.yizhaoqi.smartpai.model.AgentRuntimeStateSnapshot;
import com.yizhaoqi.smartpai.repository.AgentCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeStateServiceTest {

    @Mock
    private AgentCheckpointRepository repository;

    private ObjectMapper objectMapper;
    private AgentRuntimeStateService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AgentRuntimeStateService(
                repository,
                objectMapper,
                Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void incrementsVersionFromPersistedSnapshot() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentRuntimeStateSnapshot previous = new AgentRuntimeStateSnapshot(
                1, 3, "run-1", "RUNNING", "retrieval", null, Map.of(), Map.of(), "earlier");
        when(repository.findTopByGenerationIdAndCheckpointTypeStartingWithOrderByIdDesc(
                "run-1", "RUNTIME_STATE"))
                .thenReturn(Optional.of(checkpoint(objectMapper.writeValueAsString(previous))));

        AgentRuntimeStateSnapshot next = service.transition(
                "run-1", "WAITING_APPROVAL", "approval", "WAITING_APPROVAL", Map.of(), Map.of());

        assertThat(next.stateVersion()).isEqualTo(4);
        assertThat(next.schemaVersion()).isEqualTo(1);
        assertThat(next.updatedAt()).isEqualTo("2026-07-26T10:00:00Z");
    }

    @Test
    void rejectsTransitionFromTerminalStateBackToRunning() throws Exception {
        AgentRuntimeStateSnapshot previous = new AgentRuntimeStateSnapshot(
                1, 2, "run-1", "COMPLETED", "finalizing", "ANSWERED", Map.of(), Map.of(), "earlier");
        when(repository.findTopByGenerationIdAndCheckpointTypeStartingWithOrderByIdDesc(
                "run-1", "RUNTIME_STATE"))
                .thenReturn(Optional.of(checkpoint(objectMapper.writeValueAsString(previous))));

        assertThatThrownBy(() -> service.transition(
                "run-1", "RUNNING", "retrieval", null, Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("终态");
    }

    @Test
    void rejectsIncompatibleSchemaDuringRestore() {
        when(repository.findTopByGenerationIdAndCheckpointTypeStartingWithOrderByIdDesc(
                "run-1", "RUNTIME_STATE"))
                .thenReturn(Optional.of(checkpoint("{\"schemaVersion\":99,\"stateVersion\":1,\"generationId\":\"run-1\",\"status\":\"RUNNING\"}")));

        assertThatThrownBy(() -> service.restore("run-1"))
                .isInstanceOf(AgentRuntimeStateService.AgentCheckpointException.class)
                .hasMessageContaining("schemaVersion");
    }

    private AgentCheckpoint checkpoint(String stateJson) {
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setGenerationId("run-1");
        checkpoint.setCheckpointType("RUNTIME_STATE_V1");
        checkpoint.setStateJson(stateJson);
        return checkpoint;
    }
}

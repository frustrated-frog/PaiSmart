package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentCheckpoint;
import com.yizhaoqi.smartpai.model.AgentRuntimeStateSnapshot;
import com.yizhaoqi.smartpai.repository.AgentCheckpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 以 append-only checkpoint 保存强类型 Runtime State，并保护版本与终态不变量。 */
@Service
public class AgentRuntimeStateService {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String CHECKPOINT_PREFIX = "RUNTIME_STATE";
    private static final String CHECKPOINT_TYPE = "RUNTIME_STATE_V1";
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED", "RESUMED"
    );

    private final AgentCheckpointRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentRuntimeStateService(AgentCheckpointRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    AgentRuntimeStateService(AgentCheckpointRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public synchronized AgentRuntimeStateSnapshot transition(String generationId,
                                                             String status,
                                                             String currentNode,
                                                             String terminalReason,
                                                             Map<String, Object> budgetUsage,
                                                             Map<String, Object> taskLedger) {
        AgentRuntimeStateSnapshot previous = restore(generationId).orElse(null);
        if (previous != null && TERMINAL_STATUSES.contains(previous.status())) {
            throw new IllegalStateException("Agent Runtime 已进入终态，不能迁移回 " + status);
        }
        long nextVersion = previous == null ? 1 : previous.stateVersion() + 1;
        AgentRuntimeStateSnapshot snapshot = new AgentRuntimeStateSnapshot(
                CURRENT_SCHEMA_VERSION,
                nextVersion,
                generationId,
                status,
                currentNode,
                terminalReason,
                budgetUsage,
                taskLedger,
                Instant.now(clock).toString()
        );
        persist(snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public Optional<AgentRuntimeStateSnapshot> restore(String generationId) {
        return repository.findTopByGenerationIdAndCheckpointTypeStartingWithOrderByIdDesc(
                        generationId, CHECKPOINT_PREFIX)
                .map(this::deserializeAndValidate);
    }

    private AgentRuntimeStateSnapshot deserializeAndValidate(AgentCheckpoint checkpoint) {
        try {
            AgentRuntimeStateSnapshot snapshot = objectMapper.readValue(
                    checkpoint.getStateJson(), AgentRuntimeStateSnapshot.class);
            if (snapshot.schemaVersion() != CURRENT_SCHEMA_VERSION) {
                throw new AgentCheckpointException(
                        "不兼容的 Agent Runtime schemaVersion: " + snapshot.schemaVersion());
            }
            if (!checkpoint.getGenerationId().equals(snapshot.generationId())) {
                throw new AgentCheckpointException("Runtime Snapshot 与 generationId 不匹配");
            }
            return snapshot;
        } catch (AgentCheckpointException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentCheckpointException("Agent Runtime Snapshot 解析失败", exception);
        }
    }

    private void persist(AgentRuntimeStateSnapshot snapshot) {
        try {
            AgentCheckpoint checkpoint = new AgentCheckpoint();
            checkpoint.setGenerationId(snapshot.generationId());
            checkpoint.setCheckpointType(CHECKPOINT_TYPE);
            checkpoint.setStateJson(objectMapper.writeValueAsString(snapshot));
            checkpoint.setCreatedAt(LocalDateTime.now(clock));
            repository.save(checkpoint);
        } catch (Exception exception) {
            throw new AgentCheckpointException("关键 Agent Runtime Snapshot 写入失败", exception);
        }
    }

    public static class AgentCheckpointException extends RuntimeException {
        public AgentCheckpointException(String message) {
            super(message);
        }

        public AgentCheckpointException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

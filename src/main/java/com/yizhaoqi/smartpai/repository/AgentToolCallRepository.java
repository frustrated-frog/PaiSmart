package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {
    List<AgentToolCall> findByGenerationIdOrderByIdAsc(String generationId);

    Optional<AgentToolCall> findTopByGenerationIdAndActionFingerprintOrderByIdDesc(
            String generationId,
            String actionFingerprint
    );
}

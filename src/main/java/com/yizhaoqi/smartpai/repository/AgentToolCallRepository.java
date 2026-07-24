package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {
    Optional<AgentToolCall> findTopByGenerationIdAndActionFingerprintOrderByIdDesc(
            String generationId,
            String actionFingerprint
    );
}

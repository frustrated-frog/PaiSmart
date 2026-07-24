package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentCheckpointRepository extends JpaRepository<AgentCheckpoint, Long> {
    Optional<AgentCheckpoint> findTopByGenerationIdOrderByIdDesc(String generationId);
}

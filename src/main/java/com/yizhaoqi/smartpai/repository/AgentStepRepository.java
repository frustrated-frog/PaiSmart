package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {
    List<AgentStep> findByGenerationIdOrderByIdAsc(String generationId);
}

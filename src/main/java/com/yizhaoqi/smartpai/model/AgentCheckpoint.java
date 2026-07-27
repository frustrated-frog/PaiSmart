package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_checkpoints", indexes =
        @Index(name = "idx_agent_checkpoint_run", columnList = "generation_id,id"))
public class AgentCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generation_id", nullable = false, length = 64)
    private String generationId;

    @Column(name = "checkpoint_type", nullable = false, length = 48)
    private String checkpointType;

    @Lob
    @Column(name = "state_json", nullable = false, columnDefinition = "LONGTEXT")
    private String stateJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

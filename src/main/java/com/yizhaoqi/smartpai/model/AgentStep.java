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
@Table(name = "agent_steps", indexes = {
        @Index(name = "idx_agent_step_run_id", columnList = "generation_id,id"),
        @Index(name = "idx_agent_step_logical_id", columnList = "generation_id,step_id")
})
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generation_id", nullable = false, length = 64)
    private String generationId;

    @Column(name = "step_id", nullable = false, length = 128)
    private String stepId;

    @Column(name = "stage", nullable = false, length = 48)
    private String stage;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "detail", columnDefinition = "LONGTEXT")
    private String detail;

    @Column(name = "tool_name", length = 128)
    private String toolName;

    @Lob
    @Column(name = "metadata_json", columnDefinition = "LONGTEXT")
    private String metadataJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}

package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_runs", indexes = {
        @Index(name = "idx_agent_run_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_agent_run_conversation", columnList = "conversation_id"),
        @Index(name = "idx_agent_run_status", columnList = "status")
})
public class AgentRun {

    @Id
    @Column(name = "generation_id", length = 64)
    private String generationId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Lob
    @Column(name = "question", nullable = false)
    private String question;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "current_stage", length = 48)
    private String currentStage;

    @Column(name = "retry_of_generation_id", length = 64)
    private String retryOfGenerationId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Lob
    @Column(name = "answer")
    private String answer;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Version
    private Long version;
}

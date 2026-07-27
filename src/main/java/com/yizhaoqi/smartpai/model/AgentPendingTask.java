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
@Table(name = "agent_pending_tasks", indexes = {
        @Index(name = "idx_pending_task_user_conversation", columnList = "user_id,conversation_id,status"),
        @Index(name = "idx_pending_task_generation", columnList = "generation_id")
})
public class AgentPendingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generation_id", nullable = false, length = 64)
    private String generationId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Lob
    @Column(name = "original_query", nullable = false, columnDefinition = "LONGTEXT")
    private String originalQuery;

    @Column(name = "intent", nullable = false, length = 32)
    private String intent;

    @Lob
    @Column(name = "known_slots_json", nullable = false, columnDefinition = "LONGTEXT")
    private String knownSlotsJson;

    @Lob
    @Column(name = "missing_slots_json", nullable = false, columnDefinition = "LONGTEXT")
    private String missingSlotsJson;

    @Lob
    @Column(name = "question", nullable = false, columnDefinition = "LONGTEXT")
    private String question;

    @Lob
    @Column(name = "options_json", nullable = false, columnDefinition = "LONGTEXT")
    private String optionsJson;

    @Column(name = "resume_node", nullable = false, length = 64)
    private String resumeNode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

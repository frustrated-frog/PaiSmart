package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "agent_memories",
        uniqueConstraints = @UniqueConstraint(name = "uk_agent_memory_owner_key", columnNames = {"owner_user_id", "memory_key"}),
        indexes = {
                @Index(name = "idx_agent_memory_owner_status", columnList = "owner_user_id,status,updated_at"),
                @Index(name = "idx_agent_memory_scope", columnList = "scope_type,scope_id,status")
        })
public class AgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, length = 64)
    private String ownerUserId;

    @Column(name = "scope_type", nullable = false, length = 24)
    private String scopeType;

    @Column(name = "scope_id", nullable = false, length = 64)
    private String scopeId;

    @Column(name = "memory_type", nullable = false, length = 32)
    private String memoryType;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "memory_key", nullable = false, length = 64)
    private String memoryKey;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(name = "source_query", columnDefinition = "LONGTEXT")
    private String sourceQuery;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_reference", length = 128)
    private String sourceReference;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "access_count", nullable = false)
    private Long accessCount = 0L;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

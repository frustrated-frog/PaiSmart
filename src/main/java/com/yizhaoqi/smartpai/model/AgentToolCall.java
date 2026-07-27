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

/** 工具调用的持久化请求/结果账本，用于幂等回放和中断恢复。 */
@Data
@Entity
@Table(name = "agent_tool_calls",
        indexes = {
                @Index(name = "idx_agent_tool_call_generation", columnList = "generation_id,id"),
                @Index(name = "idx_agent_tool_call_status", columnList = "status")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_tool_call_action",
                columnNames = {"generation_id", "action_fingerprint"}
        ))
public class AgentToolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generation_id", nullable = false, length = 64)
    private String generationId;

    @Column(name = "tool_call_id", length = 128)
    private String toolCallId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "action_fingerprint", nullable = false, length = 64)
    private String actionFingerprint;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "tool_effect", nullable = false, length = 24)
    private String toolEffect;

    @Column(name = "replay_policy", nullable = false, length = 32)
    private String replayPolicy;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Lob
    @Column(name = "arguments_json", nullable = false, columnDefinition = "LONGTEXT")
    private String argumentsJson;

    @Lob
    @Column(name = "result_content", columnDefinition = "LONGTEXT")
    private String resultContent;

    @Lob
    @Column(name = "result_data_json", columnDefinition = "LONGTEXT")
    private String resultDataJson;

    @Lob
    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "reused_from_tool_call_id")
    private Long reusedFromToolCallId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}

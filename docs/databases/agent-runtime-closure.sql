-- Agent Runtime Closure schema migration (MySQL 8)
-- Apply once when ddl-auto is disabled in a controlled environment.

ALTER TABLE agent_runs
    ADD COLUMN resumed_from_checkpoint_id BIGINT NULL COMMENT '本次运行恢复自哪个 checkpoint' AFTER retry_of_generation_id;

CREATE TABLE IF NOT EXISTS agent_tool_calls (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工具调用账本主键',
    generation_id VARCHAR(64) NOT NULL COMMENT '所属 Agent 运行',
    tool_call_id VARCHAR(128) NULL COMMENT '模型返回的 tool_call_id',
    tool_name VARCHAR(64) NOT NULL COMMENT '工具名称',
    action_fingerprint VARCHAR(64) NOT NULL COMMENT '规范化动作指纹',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '幂等键',
    risk_level VARCHAR(32) NOT NULL COMMENT '风险等级',
    tool_effect VARCHAR(24) NOT NULL COMMENT 'READ/GENERATE/WRITE',
    replay_policy VARCHAR(32) NOT NULL COMMENT '回放策略',
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED/UNKNOWN/REUSED/WAITING_APPROVAL',
    arguments_json LONGTEXT NOT NULL COMMENT '工具参数 JSON',
    result_content LONGTEXT NULL COMMENT '模型可见结果',
    result_data_json LONGTEXT NULL COMMENT '结构化工具结果 JSON',
    error_message LONGTEXT NULL COMMENT '失败信息',
    reused_from_tool_call_id BIGINT NULL COMMENT '复用的原始工具调用',
    started_at DATETIME NOT NULL COMMENT '开始时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_call_action (generation_id, action_fingerprint),
    UNIQUE KEY uk_agent_tool_call_idempotency (idempotency_key),
    KEY idx_agent_tool_call_generation (generation_id, id),
    KEY idx_agent_tool_call_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具请求与结果账本';

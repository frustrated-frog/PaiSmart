package com.yizhaoqi.smartpai.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 可版本化、可恢复的 Agent 运行时事实快照。 */
public record AgentRuntimeStateSnapshot(
        int schemaVersion,
        long stateVersion,
        String generationId,
        String status,
        String currentNode,
        String terminalReason,
        Map<String, Object> budgetUsage,
        Map<String, Object> taskLedger,
        String updatedAt
) {
    public AgentRuntimeStateSnapshot {
        budgetUsage = budgetUsage == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(budgetUsage));
        taskLedger = taskLedger == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(taskLedger));
    }
}

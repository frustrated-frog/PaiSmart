package com.yizhaoqi.smartpai.model;

import java.util.List;

/** 模型可见但由运行时约束更新的外部任务工作记忆。 */
public record AgentTaskLedger(
        String goal,
        List<String> constraints,
        List<String> acceptanceCriteria,
        List<TaskItem> todos,
        List<String> blockers,
        String nextAction,
        int planVersion
) {
    public AgentTaskLedger {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        todos = todos == null ? List.of() : List.copyOf(todos);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record TaskItem(
            String id,
            String title,
            Status status,
            List<String> dependencies,
            List<String> evidenceIds
    ) {
        public TaskItem {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        BLOCKED,
        SKIPPED
    }
}

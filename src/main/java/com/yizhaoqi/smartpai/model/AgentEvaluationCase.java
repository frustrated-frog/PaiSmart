package com.yizhaoqi.smartpai.model;

import com.yizhaoqi.smartpai.evaluation.AgentEvaluationService;

import java.util.List;

/** 评测数据集中的期望 rubric；实际信号始终由服务端轨迹投影生成。 */
public record AgentEvaluationCase(
        String caseId,
        String description,
        boolean expectedClarification,
        List<String> expectedClarificationSlots,
        String expectedIntent,
        String expectedEvidenceStatus,
        boolean expectedConflict,
        List<String> expectedTrajectory,
        List<AgentEvaluationService.ToolCall> expectedTools,
        List<String> expectedCitationIds,
        List<String> requiredClaimIds,
        String expectedTerminalReason,
        List<String> generationIds
) {
    public AgentEvaluationCase {
        expectedClarificationSlots = safe(expectedClarificationSlots);
        expectedTrajectory = safe(expectedTrajectory);
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
        expectedCitationIds = safe(expectedCitationIds);
        requiredClaimIds = safe(requiredClaimIds);
        generationIds = safe(generationIds);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

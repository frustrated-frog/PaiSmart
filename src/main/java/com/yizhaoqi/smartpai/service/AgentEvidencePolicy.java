package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;

/** 将证据评估结果转换为 Agent 工具循环的收敛决策。 */
final class AgentEvidencePolicy {

    private AgentEvidencePolicy() {
    }

    static AgentTerminalReason terminalReason(EvidenceAssessment assessment) {
        if (assessment == null || assessment.suggestedAction() == null) {
            return null;
        }
        return switch (assessment.suggestedAction()) {
            case "ANSWER" -> AgentTerminalReason.ANSWERED;
            case "ABSTAIN" -> AgentTerminalReason.INSUFFICIENT_EVIDENCE;
            case "PARTIAL_ANSWER" -> AgentTerminalReason.PARTIAL_EVIDENCE;
            case "ANSWER_WITH_CONFLICTS" -> AgentTerminalReason.CONFLICTED_EVIDENCE;
            default -> null;
        };
    }
}

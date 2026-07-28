package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEvidencePolicyTest {

    @Test
    void sufficientEvidenceStopsToolLoopAndConvergesToAnswer() {
        EvidenceAssessment assessment = new EvidenceAssessment(
                EvidenceAssessment.Status.SUFFICIENT,
                0.9d,
                List.of("架构"),
                List.of(),
                List.of(),
                "signature",
                "ANSWER",
                "DETERMINISTIC",
                0
        );

        assertThat(AgentEvidencePolicy.terminalReason(assessment))
                .isEqualTo(AgentTerminalReason.ANSWERED);
    }
}

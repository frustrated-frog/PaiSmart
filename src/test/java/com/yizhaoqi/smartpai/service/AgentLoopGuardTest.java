package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopGuardTest {

    private final AgentLoopGuard guard = new AgentLoopGuard(new ObjectMapper(), new AgenticRagProperties());

    @Test
    void canonicalizesArgumentOrderAndBlocksThirdDuplicateAction() {
        Map<String, Object> firstOrder = new LinkedHashMap<>();
        firstOrder.put("query", " RRF   融合 ");
        firstOrder.put("topK", 5);
        Map<String, Object> secondOrder = new LinkedHashMap<>();
        secondOrder.put("topK", 5);
        secondOrder.put("query", "RRF 融合");

        AgentLoopGuard.ActionDecision first = guard.beforeAction("run-1", "search_knowledge", firstOrder);
        AgentLoopGuard.ActionDecision second = guard.beforeAction("run-1", "search_knowledge", secondOrder);
        AgentLoopGuard.ActionDecision third = guard.beforeAction("run-1", "search_knowledge", firstOrder);

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(first.disposition()).isEqualTo(AgentLoopGuard.Disposition.ALLOW);
        assertThat(second.disposition()).isEqualTo(AgentLoopGuard.Disposition.WARN);
        assertThat(third.disposition()).isEqualTo(AgentLoopGuard.Disposition.BLOCK);
        assertThat(third.terminalReason()).isEqualTo(AgentTerminalReason.DUPLICATE_ACTION_LIMIT);
    }

    @Test
    void stopsAfterConfiguredConsecutiveNoProgressRounds() {
        AgentLoopGuard.ProgressDecision first = guard.observeProgress("run-2", "evidence-a");
        AgentLoopGuard.ProgressDecision second = guard.observeProgress("run-2", "evidence-a");
        AgentLoopGuard.ProgressDecision third = guard.observeProgress("run-2", "evidence-a");

        assertThat(first.progressing()).isTrue();
        assertThat(second.progressing()).isTrue();
        assertThat(third.progressing()).isFalse();
        assertThat(third.terminalReason()).isEqualTo(AgentTerminalReason.NO_PROGRESS);
    }

    @Test
    void clearsWorkingStateWhenRunFinishes() {
        guard.beforeAction("run-3", "search_knowledge", Map.of("query", "RRF"));
        guard.clear("run-3");

        AgentLoopGuard.ActionDecision decision =
                guard.beforeAction("run-3", "search_knowledge", Map.of("query", "RRF"));

        assertThat(decision.occurrence()).isEqualTo(1);
    }
}

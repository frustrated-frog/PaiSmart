package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunBudgetControllerTest {

    private final AtomicLong nowNanos = new AtomicLong(1_000_000_000L);
    private AgentRunBudgetController controller;

    @BeforeEach
    void setUp() {
        AgenticRagProperties properties = new AgenticRagProperties();
        properties.getRuntime().setMaxModelTurns(2);
        properties.getRuntime().setMaxToolCalls(1);
        properties.getRuntime().setMaxPromptTokens(100);
        properties.getRuntime().setMaxCompletionTokens(50);
        properties.getRuntime().setMaxRunSeconds(10);
        controller = new AgentRunBudgetController(properties, nowNanos::get);
        controller.start("run-1");
    }

    @Test
    void blocksModelTurnAfterConfiguredLimit() {
        assertThat(controller.beforeModelTurn("run-1").allowed()).isTrue();
        assertThat(controller.beforeModelTurn("run-1").allowed()).isTrue();

        AgentRunBudgetController.BudgetDecision blocked = controller.beforeModelTurn("run-1");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.terminalReason()).isEqualTo(AgentTerminalReason.ROUND_BUDGET_EXHAUSTED);
        assertThat(blocked.usage().modelTurnsUsed()).isEqualTo(2);
    }

    @Test
    void blocksToolCallAfterConfiguredLimit() {
        assertThat(controller.beforeToolCall("run-1").allowed()).isTrue();

        AgentRunBudgetController.BudgetDecision blocked = controller.beforeToolCall("run-1");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.terminalReason()).isEqualTo(AgentTerminalReason.TOOL_BUDGET_EXHAUSTED);
    }

    @Test
    void blocksNextActionWhenTokenBudgetIsConsumed() {
        controller.recordModelUsage("run-1", 100, 10);

        AgentRunBudgetController.BudgetDecision blocked = controller.beforeModelTurn("run-1");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.terminalReason()).isEqualTo(AgentTerminalReason.TOKEN_BUDGET_EXHAUSTED);
    }

    @Test
    void blocksNextActionWhenRunTimeBudgetIsConsumed() {
        nowNanos.addAndGet(11_000_000_000L);

        AgentRunBudgetController.BudgetDecision blocked = controller.beforeToolCall("run-1");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.terminalReason()).isEqualTo(AgentTerminalReason.TIME_BUDGET_EXHAUSTED);
        assertThat(blocked.usage().elapsedMillis()).isEqualTo(11_000L);
    }

    @Test
    void clearRemovesPreviousUsage() {
        controller.beforeModelTurn("run-1");
        controller.recordModelUsage("run-1", 20, 5);

        controller.clear("run-1");
        AgentRunBudgetController.BudgetDecision restarted = controller.beforeModelTurn("run-1");

        assertThat(restarted.allowed()).isTrue();
        assertThat(restarted.usage().modelTurnsUsed()).isEqualTo(1);
        assertThat(restarted.usage().promptTokensUsed()).isZero();
    }
}

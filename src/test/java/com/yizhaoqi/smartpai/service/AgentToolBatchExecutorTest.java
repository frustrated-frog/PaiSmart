package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolBatchExecutorTest {

    private final AgentToolBatchExecutor executor = new AgentToolBatchExecutor();

    @Test
    void closesEveryToolCallAfterFirstTerminalResult() {
        List<LlmProviderRouter.ToolCallDecision> executed = new ArrayList<>();
        List<LlmProviderRouter.ToolCallDecision> calls = List.of(call("a"), call("b"), call("c"));

        AgentToolBatchExecutor.BatchResult result = executor.execute(calls, call -> {
            executed.add(call);
            return AgentToolBatchExecutor.ToolExecutionOutcome.executed(
                    "partial",
                    false,
                    AgentTerminalReason.PARTIAL_EVIDENCE
            );
        });

        assertThat(executed).extracting(LlmProviderRouter.ToolCallDecision::id)
                .containsExactly("a");
        assertThat(result.outcomes()).hasSize(3);
        assertThat(result.outcomes().get(0).status())
                .isEqualTo(AgentToolBatchExecutor.OutcomeStatus.EXECUTED);
        assertThat(result.outcomes().subList(1, 3))
                .allMatch(item -> item.status() == AgentToolBatchExecutor.OutcomeStatus.CANCELLED_BY_RUNTIME);
        assertThat(result.outcomes()).extracting(item -> item.call().id())
                .containsExactly("a", "b", "c");
        assertThat(result.terminalReason()).isEqualTo(AgentTerminalReason.PARTIAL_EVIDENCE);
        assertThat(result.outcomes().get(1).content()).contains("CANCELLED_BY_RUNTIME", "PARTIAL_EVIDENCE");
    }

    @Test
    void stopsRemainingCallsAfterStreamedToolResult() {
        List<String> executedIds = new ArrayList<>();

        AgentToolBatchExecutor.BatchResult result = executor.execute(
                List.of(call("summary"), call("feedback")),
                call -> {
                    executedIds.add(call.id());
                    return AgentToolBatchExecutor.ToolExecutionOutcome.executed("streamed", true, null);
                }
        );

        assertThat(executedIds).containsExactly("summary");
        assertThat(result.streamedToUser()).isTrue();
        assertThat(result.terminalReason()).isNull();
        assertThat(result.outcomes().get(1).status())
                .isEqualTo(AgentToolBatchExecutor.OutcomeStatus.CANCELLED_BY_RUNTIME);
    }

    @Test
    void executesAllCallsWhenNoOutcomeStopsTheBatch() {
        List<String> executedIds = new ArrayList<>();

        AgentToolBatchExecutor.BatchResult result = executor.execute(
                List.of(call("a"), call("b")),
                call -> {
                    executedIds.add(call.id());
                    return AgentToolBatchExecutor.ToolExecutionOutcome.executed("ok", false, null);
                }
        );

        assertThat(executedIds).containsExactly("a", "b");
        assertThat(result.outcomes()).allMatch(
                item -> item.status() == AgentToolBatchExecutor.OutcomeStatus.EXECUTED);
        assertThat(result.terminalReason()).isNull();
        assertThat(result.streamedToUser()).isFalse();
    }

    private LlmProviderRouter.ToolCallDecision call(String id) {
        return new LlmProviderRouter.ToolCallDecision(id, "tool_" + id, Map.of("id", id));
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 闭合一次 assistant message 中的全部 tool calls。
 * 前序调用触发终止或已经流式完成时，后续调用不再执行，但仍生成相同 call id 的取消结果。
 */
@Service
public class AgentToolBatchExecutor {

    public BatchResult execute(List<LlmProviderRouter.ToolCallDecision> calls,
                               Function<LlmProviderRouter.ToolCallDecision, ToolExecutionOutcome> operation) {
        List<LlmProviderRouter.ToolCallDecision> safeCalls = calls == null ? List.of() : List.copyOf(calls);
        List<BatchOutcome> outcomes = new ArrayList<>(safeCalls.size());
        AgentTerminalReason terminalReason = null;
        boolean streamedToUser = false;

        for (LlmProviderRouter.ToolCallDecision call : safeCalls) {
            if (terminalReason != null || streamedToUser) {
                AgentTerminalReason cancellationReason = terminalReason == null
                        ? AgentTerminalReason.ANSWERED
                        : terminalReason;
                outcomes.add(new BatchOutcome(
                        call,
                        OutcomeStatus.CANCELLED_BY_RUNTIME,
                        cancelledContent(cancellationReason),
                        false,
                        null
                ));
                continue;
            }

            ToolExecutionOutcome executed = operation.apply(call);
            if (executed == null) {
                throw new IllegalStateException("工具执行回调不能返回 null");
            }
            outcomes.add(new BatchOutcome(
                    call,
                    OutcomeStatus.EXECUTED,
                    executed.content(),
                    executed.streamedToUser(),
                    executed.terminalReason()
            ));
            if (executed.terminalReason() != null) {
                terminalReason = executed.terminalReason();
            }
            if (executed.streamedToUser()) {
                streamedToUser = true;
            }
        }
        return new BatchResult(List.copyOf(outcomes), terminalReason, streamedToUser);
    }

    private String cancelledContent(AgentTerminalReason reason) {
        return "{\"status\":\"CANCELLED_BY_RUNTIME\",\"reason\":\""
                + reason.name()
                + "\",\"message\":\"前序工具已触发运行时收敛，本调用未执行\"}";
    }

    public enum OutcomeStatus {
        EXECUTED,
        CANCELLED_BY_RUNTIME
    }

    public record ToolExecutionOutcome(String content,
                                       boolean streamedToUser,
                                       AgentTerminalReason terminalReason) {
        public ToolExecutionOutcome {
            content = content == null ? "" : content;
        }

        public static ToolExecutionOutcome executed(String content,
                                                    boolean streamedToUser,
                                                    AgentTerminalReason terminalReason) {
            return new ToolExecutionOutcome(content, streamedToUser, terminalReason);
        }
    }

    public record BatchOutcome(LlmProviderRouter.ToolCallDecision call,
                               OutcomeStatus status,
                               String content,
                               boolean streamedToUser,
                               AgentTerminalReason terminalReason) {
    }

    public record BatchResult(List<BatchOutcome> outcomes,
                              AgentTerminalReason terminalReason,
                              boolean streamedToUser) {
        public BatchResult {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }
    }
}

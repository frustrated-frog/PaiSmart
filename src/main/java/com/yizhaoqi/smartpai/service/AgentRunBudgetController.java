package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Run 级硬预算。模型只能消费预算，不能通过 Prompt 或重试重置预算。 */
@Service
public class AgentRunBudgetController {

    private final AgenticRagProperties properties;
    private final LongSupplier nanoTime;
    private final Map<String, MutableUsage> usages = new ConcurrentHashMap<>();

    public AgentRunBudgetController(AgenticRagProperties properties) {
        this(properties, System::nanoTime);
    }

    AgentRunBudgetController(AgenticRagProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    public void start(String generationId) {
        requireGenerationId(generationId);
        usages.put(generationId, new MutableUsage(nanoTime.getAsLong()));
    }

    public BudgetDecision beforeModelTurn(String generationId) {
        MutableUsage usage = usage(generationId);
        synchronized (usage) {
            BudgetDecision common = checkCommon(usage);
            if (!common.allowed()) {
                return common;
            }
            if (usage.modelTurnsUsed >= positive(properties.getRuntime().getMaxModelTurns())) {
                return denied(AgentTerminalReason.ROUND_BUDGET_EXHAUSTED, usage);
            }
            usage.modelTurnsUsed++;
            return allowed(usage);
        }
    }

    public BudgetDecision beforeToolCall(String generationId) {
        MutableUsage usage = usage(generationId);
        synchronized (usage) {
            BudgetDecision common = checkCommon(usage);
            if (!common.allowed()) {
                return common;
            }
            if (usage.toolCallsUsed >= positive(properties.getRuntime().getMaxToolCalls())) {
                return denied(AgentTerminalReason.TOOL_BUDGET_EXHAUSTED, usage);
            }
            usage.toolCallsUsed++;
            return allowed(usage);
        }
    }

    public void recordModelUsage(String generationId, int promptTokens, int completionTokens) {
        MutableUsage usage = usage(generationId);
        synchronized (usage) {
            usage.promptTokensUsed += Math.max(0, promptTokens);
            usage.completionTokensUsed += Math.max(0, completionTokens);
        }
    }

    public BudgetUsage snapshot(String generationId) {
        MutableUsage usage = usage(generationId);
        synchronized (usage) {
            return snapshot(usage);
        }
    }

    public void clear(String generationId) {
        if (generationId != null) {
            usages.remove(generationId);
        }
    }

    private BudgetDecision checkCommon(MutableUsage usage) {
        if (elapsedMillis(usage) >= positive(properties.getRuntime().getMaxRunSeconds()) * 1000L) {
            return denied(AgentTerminalReason.TIME_BUDGET_EXHAUSTED, usage);
        }
        if (usage.promptTokensUsed >= positive(properties.getRuntime().getMaxPromptTokens())
                || usage.completionTokensUsed >= positive(properties.getRuntime().getMaxCompletionTokens())) {
            return denied(AgentTerminalReason.TOKEN_BUDGET_EXHAUSTED, usage);
        }
        return allowed(usage);
    }

    private BudgetDecision allowed(MutableUsage usage) {
        return new BudgetDecision(true, null, snapshot(usage));
    }

    private BudgetDecision denied(AgentTerminalReason reason, MutableUsage usage) {
        return new BudgetDecision(false, reason, snapshot(usage));
    }

    private BudgetUsage snapshot(MutableUsage usage) {
        return new BudgetUsage(
                usage.modelTurnsUsed,
                usage.toolCallsUsed,
                usage.promptTokensUsed,
                usage.completionTokensUsed,
                elapsedMillis(usage)
        );
    }

    private long elapsedMillis(MutableUsage usage) {
        return Math.max(0L, (nanoTime.getAsLong() - usage.startedAtNanos) / 1_000_000L);
    }

    private MutableUsage usage(String generationId) {
        requireGenerationId(generationId);
        return usages.computeIfAbsent(generationId, ignored -> new MutableUsage(nanoTime.getAsLong()));
    }

    private int positive(int value) {
        return Math.max(1, value);
    }

    private void requireGenerationId(String generationId) {
        if (generationId == null || generationId.isBlank()) {
            throw new IllegalArgumentException("generationId 不能为空");
        }
    }

    private static final class MutableUsage {
        private final long startedAtNanos;
        private int modelTurnsUsed;
        private int toolCallsUsed;
        private int promptTokensUsed;
        private int completionTokensUsed;

        private MutableUsage(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }
    }

    public record BudgetDecision(boolean allowed,
                                 AgentTerminalReason terminalReason,
                                 BudgetUsage usage) {
    }

    public record BudgetUsage(int modelTurnsUsed,
                              int toolCallsUsed,
                              int promptTokensUsed,
                              int completionTokensUsed,
                              long elapsedMillis) {
    }
}

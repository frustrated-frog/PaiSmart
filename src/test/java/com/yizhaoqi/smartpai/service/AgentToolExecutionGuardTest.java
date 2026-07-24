package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolExecutionGuardTest {

    private AgentToolExecutionGuard guard;

    @BeforeEach
    void setUp() {
        AgenticRagProperties properties = new AgenticRagProperties();
        properties.getTools().setCircuitFailureThreshold(2);
        properties.getTools().setCircuitCooldownSeconds(60);
        guard = new AgentToolExecutionGuard(properties, Runnable::run);
    }

    @Test
    void returnsSuccessfulToolResult() {
        String result = guard.execute(
                "search_knowledge",
                "42",
                AgentToolExecutionGuard.RiskLevel.READ_ONLY,
                () -> "ok"
        );
        assertEquals("ok", result);
    }

    @Test
    void opensCircuitAfterConsecutiveFailures() {
        for (int index = 0; index < 2; index++) {
            assertThrows(IllegalStateException.class, () -> guard.execute(
                    "generate_summary",
                    "42",
                    AgentToolExecutionGuard.RiskLevel.GENERATIVE,
                    () -> {
                        throw new IllegalStateException("provider down");
                    }
            ));
        }

        IllegalStateException open = assertThrows(IllegalStateException.class, () -> guard.execute(
                "generate_summary",
                "42",
                AgentToolExecutionGuard.RiskLevel.GENERATIVE,
                () -> "should-not-run"
        ));
        assertEquals("工具暂时熔断，请稍后重试: generate_summary", open.getMessage());
    }
}

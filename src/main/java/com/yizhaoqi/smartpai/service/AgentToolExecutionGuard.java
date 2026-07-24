package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** 工具执行隔离层：用户级并发限制、超时、熔断与风险分级。 */
@Service
public class AgentToolExecutionGuard {

    private final AgenticRagProperties properties;
    private final Executor executor;
    private final Map<String, Semaphore> userPermits = new ConcurrentHashMap<>();
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public AgentToolExecutionGuard(AgenticRagProperties properties,
                                   @Qualifier("agentToolExecutor") Executor executor) {
        this.properties = properties;
        this.executor = executor;
    }

    public <T> T execute(String toolName, String userId, RiskLevel riskLevel, Supplier<T> operation) {
        validate(toolName, userId, riskLevel);
        AgenticRagProperties.Tools config = properties.getTools();
        CircuitState circuit = circuits.computeIfAbsent(toolName, ignored -> new CircuitState());
        if (circuit.isOpen(config.getCircuitCooldownSeconds())) {
            throw new IllegalStateException("工具暂时熔断，请稍后重试: " + toolName);
        }

        Semaphore permit = userPermits.computeIfAbsent(
                userId,
                ignored -> new Semaphore(Math.max(1, config.getMaxConcurrentPerUser()))
        );
        if (!permit.tryAcquire()) {
            throw new IllegalStateException("当前用户并发工具调用已达到上限");
        }

        CompletableFuture<T> future = CompletableFuture.supplyAsync(operation, executor);
        try {
            T result = future.get(Math.max(1, config.getTimeoutSeconds()), TimeUnit.SECONDS);
            circuit.success();
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            circuit.failure(config.getCircuitFailureThreshold());
            throw new IllegalStateException("工具执行超时: " + toolName, exception);
        } catch (Exception exception) {
            circuit.failure(config.getCircuitFailureThreshold());
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("工具执行失败: " + toolName, cause);
        } finally {
            permit.release();
        }
    }

    private void validate(String toolName, String userId, RiskLevel riskLevel) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("工具调用缺少用户身份");
        }
        if (riskLevel == null) {
            throw new IllegalArgumentException("工具必须声明风险等级");
        }
    }

    public enum RiskLevel {
        READ_ONLY,
        GENERATIVE,
        WRITE_USER_SCOPE
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private Instant openedAt;

        private synchronized boolean isOpen(int cooldownSeconds) {
            if (openedAt == null) {
                return false;
            }
            if (Instant.now().isAfter(openedAt.plusSeconds(Math.max(1, cooldownSeconds)))) {
                consecutiveFailures = 0;
                openedAt = null;
                return false;
            }
            return true;
        }

        private synchronized void success() {
            consecutiveFailures = 0;
            openedAt = null;
        }

        private synchronized void failure(int threshold) {
            consecutiveFailures++;
            if (consecutiveFailures >= Math.max(1, threshold)) {
                openedAt = Instant.now();
            }
        }
    }
}

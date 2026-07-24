package com.yizhaoqi.smartpai.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 将供应商异常转换为可展示、可持久化的稳定错误语义。
 * 完整异常只进入服务端日志，避免在 WebSocket 轨迹和 Agent 运行记录中泄露 URL、密钥或响应体。
 */
@Component
public class AgentErrorSanitizer {

    private static final int MAX_AUDIT_MESSAGE_LENGTH = 200;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[-_ ]?key\\s*[:=]\\s*)\\S+");
    private static final Pattern SECRET_TOKEN_PATTERN = Pattern.compile("\\b(?:sk|ak)-[A-Za-z0-9_-]{8,}\\b");

    public String userMessage(Throwable error) {
        return classify(rootMessage(error)).userMessage;
    }

    public String auditMessage(Throwable error) {
        return auditMessage(rootMessage(error));
    }

    public String auditMessage(String rawMessage) {
        ErrorCategory category = classify(rawMessage);
        if (category != ErrorCategory.UNKNOWN) {
            return category.auditMessage;
        }
        if (rawMessage == null || rawMessage.isBlank()) {
            return ErrorCategory.UNKNOWN.auditMessage;
        }
        String sanitized = URL_PATTERN.matcher(rawMessage.trim()).replaceAll("[provider-endpoint]");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer [redacted]");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1[redacted]");
        sanitized = SECRET_TOKEN_PATTERN.matcher(sanitized).replaceAll("[redacted-token]");
        return sanitized.length() <= MAX_AUDIT_MESSAGE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_AUDIT_MESSAGE_LENGTH) + "...";
    }

    private ErrorCategory classify(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "401", "403", "unauthorized", "forbidden", "authentication fails", "invalid api key")) {
            return ErrorCategory.AUTHENTICATION;
        }
        if (containsAny(normalized, "429", "too many requests", "rate limit", "quota exceeded")) {
            return ErrorCategory.RATE_LIMIT;
        }
        if (containsAny(normalized, "timeout", "timed out", "超时")) {
            return ErrorCategory.TIMEOUT;
        }
        if (containsAny(normalized, "connection refused", "connection reset", "unknown host", "dns", "network")) {
            return ErrorCategory.NETWORK;
        }
        return ErrorCategory.UNKNOWN;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String rootMessage(Throwable error) {
        if (error == null) {
            return "";
        }
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum ErrorCategory {
        AUTHENTICATION(
                "模型服务认证失败，请在「模型配置」中更新 API Key 或启用备用模型",
                "模型供应商认证失败（凭据或权限无效）"
        ),
        RATE_LIMIT(
                "模型服务当前限流，请稍后重试或切换备用模型",
                "模型供应商触发限流或配额不足"
        ),
        TIMEOUT(
                "模型服务响应超时，Agent 已安全中断，请稍后重试",
                "模型供应商调用超时"
        ),
        NETWORK(
                "模型服务网络连接失败，Agent 已安全中断，请稍后重试",
                "模型供应商网络连接失败"
        ),
        UNKNOWN(
                "AI 服务暂时不可用，Agent 运行记录已保留",
                "Agent 执行失败，详细原因仅保留在服务端日志"
        );

        private final String userMessage;
        private final String auditMessage;

        ErrorCategory(String userMessage, String auditMessage) {
            this.userMessage = userMessage;
            this.auditMessage = auditMessage;
        }
    }
}

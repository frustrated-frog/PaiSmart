package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;

/** 将任意工具异常转换为可审计、可决策且不泄露内部细节的错误协议。 */
@Service
public class AgentToolErrorClassifier {

    public AgentToolError classify(Throwable throwable) {
        Throwable matched = findKnownCause(throwable);
        if (matched instanceof RateLimitExceededException rateLimit) {
            return new AgentToolError(
                    ErrorType.RATE_LIMITED, true, "工具请求达到限流，请稍后重试",
                    "RETRY_WITH_BACKOFF", Math.toIntExact(Math.min(Integer.MAX_VALUE, rateLimit.getRetryAfterSeconds()))
            );
        }
        if (matched instanceof TimeoutException) {
            return new AgentToolError(
                    ErrorType.TIMEOUT, true, "工具执行超时",
                    "RETRY_WITH_SMALLER_SCOPE", null
            );
        }
        if (matched instanceof AccessDeniedException || matched instanceof SecurityException) {
            return new AgentToolError(
                    ErrorType.PERMISSION_DENIED, false, "当前用户没有执行该工具的权限",
                    "REQUEST_PERMISSION", null
            );
        }
        if (matched instanceof NoSuchElementException) {
            return new AgentToolError(
                    ErrorType.NOT_FOUND, false, "工具所需资源不存在",
                    "CLARIFY_RESOURCE", null
            );
        }
        if (matched instanceof IllegalArgumentException) {
            return new AgentToolError(
                    ErrorType.INVALID_ARGUMENT, false, "工具参数不合法",
                    "REWRITE_ARGUMENTS", null
            );
        }
        return new AgentToolError(
                ErrorType.INTERNAL, false, "工具执行失败",
                "STOP_OR_FALLBACK", null
        );
    }

    private Throwable findKnownCause(Throwable throwable) {
        Throwable current = throwable == null ? new RuntimeException("unknown") : throwable;
        Throwable fallback = current;
        while (current != null) {
            if (current instanceof RateLimitExceededException
                    || current instanceof TimeoutException
                    || current instanceof AccessDeniedException
                    || current instanceof SecurityException
                    || current instanceof NoSuchElementException
                    || current instanceof IllegalArgumentException) {
                return current;
            }
            fallback = current;
            current = current.getCause();
        }
        return fallback;
    }

    public enum ErrorType {
        INVALID_ARGUMENT,
        PERMISSION_DENIED,
        NOT_FOUND,
        RATE_LIMITED,
        TIMEOUT,
        UPSTREAM,
        INTERNAL
    }

    public record AgentToolError(ErrorType type,
                                 boolean retryable,
                                 String safeMessage,
                                 String suggestedAction,
                                 Integer retryAfterSeconds) {
    }
}

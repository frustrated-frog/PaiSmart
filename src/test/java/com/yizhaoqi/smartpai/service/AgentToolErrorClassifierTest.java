package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolErrorClassifierTest {

    private final AgentToolErrorClassifier classifier = new AgentToolErrorClassifier();

    @Test
    void classifiesInvalidArgumentsAsNonRetryable() {
        AgentToolErrorClassifier.AgentToolError error = classifier.classify(
                new IllegalArgumentException("topK 非法")
        );

        assertThat(error.type()).isEqualTo(AgentToolErrorClassifier.ErrorType.INVALID_ARGUMENT);
        assertThat(error.retryable()).isFalse();
        assertThat(error.suggestedAction()).isEqualTo("REWRITE_ARGUMENTS");
    }

    @Test
    void classifiesTimeoutAsRetryable() {
        AgentToolErrorClassifier.AgentToolError error = classifier.classify(
                new IllegalStateException("wrapper", new TimeoutException("slow"))
        );

        assertThat(error.type()).isEqualTo(AgentToolErrorClassifier.ErrorType.TIMEOUT);
        assertThat(error.retryable()).isTrue();
        assertThat(error.safeMessage()).doesNotContain("slow");
    }

    @Test
    void keepsRateLimitRetryAfterSeconds() {
        AgentToolErrorClassifier.AgentToolError error = classifier.classify(
                new RateLimitExceededException("quota detail", 17)
        );

        assertThat(error.type()).isEqualTo(AgentToolErrorClassifier.ErrorType.RATE_LIMITED);
        assertThat(error.retryable()).isTrue();
        assertThat(error.retryAfterSeconds()).isEqualTo(17);
    }

    @Test
    void defaultsUnknownFailuresToNonRetryableInternalError() {
        AgentToolErrorClassifier.AgentToolError error = classifier.classify(
                new RuntimeException("secret upstream payload")
        );

        assertThat(error.type()).isEqualTo(AgentToolErrorClassifier.ErrorType.INTERNAL);
        assertThat(error.retryable()).isFalse();
        assertThat(error.safeMessage()).doesNotContain("secret");
    }
}

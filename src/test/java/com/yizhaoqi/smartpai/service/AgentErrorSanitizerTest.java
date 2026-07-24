package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentErrorSanitizerTest {

    private final AgentErrorSanitizer sanitizer = new AgentErrorSanitizer();

    @Test
    void shouldMapAuthenticationFailureWithoutLeakingProviderEndpoint() {
        RuntimeException error = new RuntimeException(
                "401 Unauthorized from POST https://api.example.com/v1/chat/completions?api_key=secret"
        );

        assertThat(sanitizer.auditMessage(error))
                .isEqualTo("模型供应商认证失败（凭据或权限无效）")
                .doesNotContain("https://", "secret");
        assertThat(sanitizer.userMessage(error)).contains("模型配置");
    }

    @Test
    void shouldRedactUnknownProviderDetailsBeforePersisting() {
        String raw = "provider failed at https://internal.example/v1 with Bearer abc.def and apiKey=top-secret";

        assertThat(sanitizer.auditMessage(raw))
                .contains("[provider-endpoint]", "Bearer [redacted]", "apiKey=[redacted]")
                .doesNotContain("internal.example", "abc.def", "top-secret");
    }

    @Test
    void shouldReturnStableMessagesForOperationalCategories() {
        assertThat(sanitizer.auditMessage("429 Too Many Requests")).contains("限流");
        assertThat(sanitizer.auditMessage("upstream timed out")).contains("超时");
        assertThat(sanitizer.auditMessage("connection refused")).contains("网络");
    }
}

package com.yizhaoqi.smartpai.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestTest {

    @Test
    void shouldNeverExposeCredentialsThroughToString() {
        UserRequest request = new UserRequest("admin", "plain-password", "invite-secret");

        assertThat(request.toString())
                .contains("admin", "[REDACTED]")
                .doesNotContain("plain-password", "invite-secret");
    }
}

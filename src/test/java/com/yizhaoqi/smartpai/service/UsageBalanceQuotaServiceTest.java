package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.UsageQuotaProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageBalanceQuotaServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private UserTokenService userTokenService;

    @Mock
    private UserRepository userRepository;

    private UsageQuotaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new UsageQuotaProperties();
        properties.getLocalAdminBypass().setEnabled(true);
        properties.getLocalAdminBypass().setUsername("admin");
    }

    @Test
    void configuredLocalAdminBypassesLlmAndEmbeddingBudgets() {
        UsageBalanceQuotaService service = createService();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("admin", User.Role.ADMIN)));

        UsageQuotaService.TokenReservationBundle llm = service.reserveLlmTokensWithGlobalBudget(
                "1", 10_000, 4_000, 1, 60, 1, 86400);
        UsageQuotaService.TokenReservationBundle embedding = service.reserveEmbeddingTokensWithGlobalBudget(
                "1", List.of("需要生成向量的测试文本"), "embedding-upload",
                "minute exceeded", "day exceeded", 1, 60, 1, 86400);

        assertTrue(llm.noop());
        assertTrue(embedding.noop());
        verifyNoInteractions(stringRedisTemplate, userTokenService);
    }

    @Test
    void ordinaryUserStillUsesEmbeddingBalanceChecks() {
        UsageBalanceQuotaService service = createService();
        List<String> texts = List.of("需要生成向量的测试文本");
        int estimatedTokens = service.estimateEmbeddingTokens(texts);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user("member", User.Role.USER)));
        when(userTokenService.hasEnoughEmbeddingTokens("2", estimatedTokens)).thenReturn(false);
        when(userTokenService.getEmbeddingTokenBalance("2")).thenReturn(0L);

        assertThrows(RateLimitExceededException.class, () -> service.reserveEmbeddingTokens(
                "2", texts));

        verify(userTokenService).hasEnoughEmbeddingTokens("2", estimatedTokens);
    }

    @Test
    void disabledBypassKeepsAdminBalanceChecksEnabled() {
        properties.getLocalAdminBypass().setEnabled(false);
        UsageBalanceQuotaService service = createService();
        when(userTokenService.hasEnoughLlmTokens("1", 30)).thenReturn(false);
        when(userTokenService.getLlmTokenBalance("1")).thenReturn(0L);

        assertThrows(RateLimitExceededException.class,
                () -> service.reserveLlmTokens("1", 10, 20));

        verify(userRepository, never()).findById(1L);
        verify(userTokenService).hasEnoughLlmTokens("1", 30);
    }

    private UsageBalanceQuotaService createService() {
        return new UsageBalanceQuotaService(
                stringRedisTemplate,
                properties,
                userTokenService,
                userRepository
        );
    }

    private User user(String username, User.Role role) {
        User user = new User();
        user.setId("admin".equals(username) ? 1L : 2L);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}

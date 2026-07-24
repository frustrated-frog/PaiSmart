package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentMemory;
import com.yizhaoqi.smartpai.repository.AgentMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMemoryServiceTest {

    private AgentMemoryRepository repository;
    private AgentMemoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgentMemoryRepository.class);
        service = new AgentMemoryService(repository);
        when(repository.findByOwnerUserIdAndMemoryKey(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(AgentMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void keepsUnstructuredNegativeFeedbackProposed() {
        AgentMemory memory = service.recordFeedback(
                "42", "bad", "用户点击点踩", "什么是 RRF", null, "run-1");

        assertEquals("CORRECTION", memory.getMemoryType());
        assertEquals("PROPOSED", memory.getStatus());
        assertNotNull(memory.getMemoryKey());
    }

    @Test
    void activatesExplicitCorrectionWithHigherConfidence() {
        AgentMemory memory = service.recordFeedback(
                "42", "bad", "回答事实错误", "RRF 参数是多少", "RRF 的 rank constant 配置为 60", "run-2");

        assertEquals("ACTIVE", memory.getStatus());
        assertEquals(0.95d, memory.getConfidence());
        assertEquals("RRF 的 rank constant 配置为 60", memory.getContent());
    }
}

package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.AgentPendingTask;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.repository.AgentPendingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPendingTaskServiceTest {

    @Mock
    private AgentPendingTaskRepository repository;

    private AgentPendingTaskService service;

    @BeforeEach
    void setUp() {
        service = new AgentPendingTaskService(repository, new ObjectMapper());
        when(repository.save(any(AgentPendingTask.class))).thenAnswer(invocation -> {
            AgentPendingTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(11L);
            }
            return task;
        });
    }

    @Test
    void createsPendingTaskAndMergesSingleMissingSlot() {
        when(repository.findByUserIdAndConversationIdAndStatus("7", "conversation-1", "WAITING_CLARIFICATION"))
                .thenReturn(List.of());
        QueryPlan plan = new QueryPlan(
                "帮我删除一下",
                QueryPlan.Intent.ACTION,
                QueryPlan.Complexity.SIMPLE,
                false,
                true,
                List.of(),
                List.of(),
                Map.of("action", "delete"),
                List.of("actionTarget"),
                "你希望删除哪个对象？",
                List.of(),
                List.of(),
                0.8d,
                "TEST"
        );

        AgentPendingTask task = service.create("run-1", "7", "conversation-1", plan, "QUERY_PLANNING");
        when(repository.findTopByUserIdAndConversationIdAndStatusOrderByIdDesc(
                "7", "conversation-1", "WAITING_CLARIFICATION"))
                .thenReturn(Optional.of(task));

        AgentPendingTaskService.ResolvedClarification resolved = service
                .consume("7", "conversation-1", "测试知识库")
                .orElseThrow();

        assertThat(resolved.sourceGenerationId()).isEqualTo("run-1");
        assertThat(resolved.mergedQuery()).contains("帮我删除一下", "测试知识库");
        assertThat(resolved.slots()).containsEntry("action", "delete").containsEntry("actionTarget", "测试知识库");
        assertThat(task.getStatus()).isEqualTo("RESUMED");
    }

    @Test
    void expiresStalePendingTaskInsteadOfMergingAnswer() {
        AgentPendingTask task = new AgentPendingTask();
        task.setId(12L);
        task.setUserId("7");
        task.setConversationId("conversation-1");
        task.setStatus("WAITING_CLARIFICATION");
        task.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findTopByUserIdAndConversationIdAndStatusOrderByIdDesc(
                "7", "conversation-1", "WAITING_CLARIFICATION"))
                .thenReturn(Optional.of(task));

        Optional<AgentPendingTaskService.ResolvedClarification> result =
                service.consume("7", "conversation-1", "补充信息");

        assertThat(result).isEmpty();
        assertThat(task.getStatus()).isEqualTo("EXPIRED");
    }
}

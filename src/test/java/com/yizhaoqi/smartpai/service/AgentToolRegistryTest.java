package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.rag.AgenticRetrievalService;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.rag.model.RetrievalOutcome;
import com.yizhaoqi.smartpai.rag.model.RetrievalTrace;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.client.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    @Test
    void searchToolReusesRunQueryPlan() {
        AgenticRetrievalService retrievalService = mock(AgenticRetrievalService.class);
        QueryPlan plan = plan();
        RetrievalOutcome outcome = new RetrievalOutcome(
                List.of(),
                new RetrievalTrace("trace", plan, List.of(), List.of(), 1L),
                null,
                0
        );
        when(retrievalService.retrieve(plan, "7", 5)).thenReturn(outcome);
        AgentToolRegistry registry = registry(retrievalService);

        AgentToolRegistry.ToolExecutionResult result = registry.executeTool(
                "search_knowledge",
                Map.of("query", "模型生成的查询", "topK", 5),
                "7",
                null,
                plan
        );

        assertThat(result.success()).isTrue();
        verify(retrievalService).retrieve(plan, "7", 5);
        verify(retrievalService, never()).retrieve("模型生成的查询", "7", 5);
    }

    private AgentToolRegistry registry(AgenticRetrievalService retrievalService) {
        AgenticRagProperties properties = new AgenticRagProperties();
        AgentToolExecutionGuard guard = new AgentToolExecutionGuard(properties, Runnable::run);
        return new AgentToolRegistry(
                retrievalService,
                mock(DeepSeekClient.class),
                mock(StringRedisTemplate.class),
                mock(ElasticsearchClient.class),
                mock(FileUploadRepository.class),
                mock(AgentMemoryService.class),
                guard
        );
    }

    private QueryPlan plan() {
        return new QueryPlan(
                "原始用户问题",
                QueryPlan.Intent.KNOWLEDGE_QA,
                QueryPlan.Complexity.SIMPLE,
                true,
                false,
                List.of("知枢"),
                List.of(),
                List.of(new QueryPlan.QueryVariant(
                        QueryPlan.VariantType.ORIGINAL,
                        "原始用户问题",
                        "原始查询"
                )),
                0.9d,
                "TEST"
        );
    }
}

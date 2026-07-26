package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.rag.model.RetrievalOutcome;
import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenticRetrievalServiceTest {

    private QueryPlanningService queryPlanningService;
    private AgenticRetrievalService service;

    @BeforeEach
    void setUp() {
        queryPlanningService = mock(QueryPlanningService.class);
        service = new AgenticRetrievalService(
                queryPlanningService,
                mock(HybridSearchService.class),
                mock(RerankerService.class),
                mock(ParentContextAssembler.class),
                mock(EvidenceVerifierService.class),
                mock(CorrectiveQueryRefiner.class),
                new AgenticRagProperties(),
                Runnable::run
        );
    }

    @Test
    void reusesProvidedQueryPlanWithoutPlanningAgain() {
        QueryPlan plan = plan("你好", QueryPlan.Intent.CHAT, false);

        RetrievalOutcome outcome = service.retrieve(plan, "7", 5);

        assertThat(outcome.trace().queryPlan()).isSameAs(plan);
        assertThat(outcome.results()).isEmpty();
        verify(queryPlanningService, never()).plan(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void stringEntryPointPlansExactlyOnce() {
        QueryPlan plan = plan("你好", QueryPlan.Intent.CHAT, false);
        when(queryPlanningService.plan("你好", "7")).thenReturn(plan);

        RetrievalOutcome outcome = service.retrieve("你好", "7", 5);

        assertThat(outcome.trace().queryPlan()).isSameAs(plan);
        verify(queryPlanningService).plan("你好", "7");
    }

    private QueryPlan plan(String query, QueryPlan.Intent intent, boolean retrievalRequired) {
        return new QueryPlan(
                query,
                intent,
                QueryPlan.Complexity.SIMPLE,
                retrievalRequired,
                false,
                List.of(),
                List.of(),
                List.of(new QueryPlan.QueryVariant(QueryPlan.VariantType.ORIGINAL, query, "原始查询")),
                0.9d,
                "TEST"
        );
    }
}

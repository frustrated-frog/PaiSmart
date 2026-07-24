package com.yizhaoqi.smartpai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.service.LlmProviderRouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class QueryPlanningServiceTest {

    @Test
    void rulePlannerKeepsOriginalAndDecomposesComplexComparison() {
        AgenticRagProperties properties = new AgenticRagProperties();
        properties.getQueryPlanning().setLlmEnabled(false);
        properties.getQueryPlanning().setMaxVariants(4);
        LlmProviderRouter router = mock(LlmProviderRouter.class);
        QueryPlanningService service = new QueryPlanningService(router, new ObjectMapper(), properties);

        QueryPlan plan = service.plan("比较 BM25 与向量召回，并且说明 RRF 如何融合", "1");

        assertEquals(QueryPlan.Intent.COMPARE, plan.intent());
        assertEquals(QueryPlan.Complexity.COMPLEX, plan.complexity());
        assertEquals(QueryPlan.VariantType.ORIGINAL, plan.variants().get(0).type());
        assertTrue(plan.variants().stream().anyMatch(item -> item.type() == QueryPlan.VariantType.DECOMPOSED));
        verifyNoInteractions(router);
    }
}

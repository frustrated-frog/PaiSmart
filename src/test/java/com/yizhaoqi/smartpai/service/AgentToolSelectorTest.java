package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolSelectorTest {

    private final AgentToolSelector selector = new AgentToolSelector();

    @Test
    void hidesRetrievalToolsWhenPlanDoesNotRequireRetrieval() {
        QueryPlan plan = plan(QueryPlan.Intent.CHAT, false);

        List<AgentToolRegistry.AgentTool> selected = selector.select(
                plan,
                tools("search_knowledge", "generate_summary", "submit_feedback")
        );

        assertThat(selected).extracting(AgentToolRegistry.AgentTool::name)
                .containsExactly("submit_feedback");
    }

    @Test
    void summaryIntentAvoidsDuplicateSearchTool() {
        QueryPlan plan = plan(QueryPlan.Intent.SUMMARY, true);

        List<AgentToolRegistry.AgentTool> selected = selector.select(
                plan,
                tools("search_knowledge", "generate_summary", "submit_feedback")
        );

        assertThat(selected).extracting(AgentToolRegistry.AgentTool::name)
                .containsExactly("generate_summary", "submit_feedback");
    }

    @Test
    void keepsAllToolsForKnowledgePlan() {
        QueryPlan plan = plan(QueryPlan.Intent.KNOWLEDGE_QA, true);
        List<AgentToolRegistry.AgentTool> tools = tools(
                "search_knowledge", "generate_summary", "submit_feedback", "knowledge_stats"
        );

        assertThat(selector.select(plan, tools)).containsExactlyElementsOf(tools);
    }

    private QueryPlan plan(QueryPlan.Intent intent, boolean retrievalRequired) {
        return new QueryPlan(
                "测试问题",
                intent,
                QueryPlan.Complexity.SIMPLE,
                retrievalRequired,
                false,
                List.of(),
                List.of(),
                List.of(new QueryPlan.QueryVariant(QueryPlan.VariantType.ORIGINAL, "测试问题", "原始问题")),
                0.9d,
                "TEST"
        );
    }

    private List<AgentToolRegistry.AgentTool> tools(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> new AgentToolRegistry.AgentTool(
                        name,
                        name,
                        Map.of("type", "object"),
                        new AgentToolRegistry.ToolPolicy(
                                AgentToolExecutionGuard.RiskLevel.READ_ONLY,
                                AgentToolRegistry.ToolEffect.READ,
                                AgentToolRegistry.ConcurrencyPolicy.PARALLEL_SAFE,
                                AgentToolRegistry.ReplayPolicy.REPLAY_SAFE
                        )
                ))
                .toList();
    }
}

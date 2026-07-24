package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.AgentTaskLedger;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskLedgerServiceTest {

    private final AgentTaskLedgerService service = new AgentTaskLedgerService(new ObjectMapper());

    @Test
    void createsAndAdvancesLedgerFromEvidence() {
        QueryPlan plan = complexPlan();
        AgentTaskLedger created = service.initialize("run-1", plan).orElseThrow();

        assertThat(created.todos()).extracting(AgentTaskLedger.TaskItem::status)
                .containsExactly(
                        AgentTaskLedger.Status.IN_PROGRESS,
                        AgentTaskLedger.Status.PENDING,
                        AgentTaskLedger.Status.PENDING,
                        AgentTaskLedger.Status.PENDING
                );

        SearchResult result = new SearchResult("md5", 3, "RRF evidence", 0.9);
        EvidenceAssessment assessment = new EvidenceAssessment(
                EvidenceAssessment.Status.SUFFICIENT,
                0.91,
                List.of("BM25", "向量召回"),
                List.of(),
                List.of(),
                "signature",
                "ANSWER",
                "rule",
                0
        );
        AgentTaskLedger updated = service.observeToolResult(
                "run-1",
                "search_knowledge",
                Map.of("results", List.of(result), "evidenceAssessment", assessment)
        ).orElseThrow();

        assertThat(updated.todos()).extracting(AgentTaskLedger.TaskItem::status)
                .containsExactly(
                        AgentTaskLedger.Status.COMPLETED,
                        AgentTaskLedger.Status.COMPLETED,
                        AgentTaskLedger.Status.COMPLETED,
                        AgentTaskLedger.Status.IN_PROGRESS
                );
        assertThat(service.buildModelGuidance("run-1")).contains("不要重复已经完成的步骤");
    }

    @Test
    void restoresSerializedLedgerAndClosesUnfinishedTasksOnAnswer() {
        AgentTaskLedger original = service.initialize("source", complexPlan()).orElseThrow();
        Map<?, ?> serialized = new ObjectMapper().convertValue(original, Map.class);

        AgentTaskLedger restored = service.restore("retry", serialized).orElseThrow();
        AgentTaskLedger completed = service.completeAnswer("retry").orElseThrow();

        assertThat(restored.goal()).isEqualTo(original.goal());
        assertThat(completed.todos()).noneMatch(item -> item.status() == AgentTaskLedger.Status.IN_PROGRESS);
        assertThat(completed.todos()).filteredOn(item -> "answer".equals(item.id()))
                .extracting(AgentTaskLedger.TaskItem::status)
                .containsExactly(AgentTaskLedger.Status.COMPLETED);
    }

    @Test
    void skipsLedgerForSimpleChat() {
        QueryPlan simple = new QueryPlan(
                "你好", QueryPlan.Intent.CHAT, QueryPlan.Complexity.SIMPLE,
                false, false, List.of(), List.of(), List.of(), 0.99, "rule"
        );

        assertThat(service.initialize("simple", simple)).isEmpty();
    }

    private QueryPlan complexPlan() {
        return new QueryPlan(
                "比较 BM25 和向量召回后解释 RRF",
                QueryPlan.Intent.COMPARE,
                QueryPlan.Complexity.COMPLEX,
                true,
                false,
                List.of("BM25", "向量召回", "RRF"),
                List.of("给出引用"),
                List.of(
                        new QueryPlan.QueryVariant(QueryPlan.VariantType.DECOMPOSED, "BM25 召回", "子问题"),
                        new QueryPlan.QueryVariant(QueryPlan.VariantType.DECOMPOSED, "向量召回", "子问题")
                ),
                0.92,
                "llm"
        );
    }
}

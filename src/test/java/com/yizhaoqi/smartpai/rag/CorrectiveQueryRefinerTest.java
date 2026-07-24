package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrectiveQueryRefinerTest {

    @Test
    void onlyReturnsQueriesForMissingAspectsAndSkipsExecutedQueries() {
        CorrectiveQueryRefiner refiner = new CorrectiveQueryRefiner(new AgenticRagProperties());
        QueryPlan plan = new QueryPlan(
                "比较 BM25 与向量召回",
                QueryPlan.Intent.COMPARE,
                QueryPlan.Complexity.COMPLEX,
                true,
                false,
                List.of("BM25", "向量召回"),
                List.of("知识库"),
                List.of(),
                0.9d,
                "TEST"
        );
        EvidenceAssessment assessment = new EvidenceAssessment(
                EvidenceAssessment.Status.PARTIAL,
                0.6d,
                List.of("BM25"),
                List.of("向量召回"),
                List.of(),
                "sig",
                "REFINE_RETRIEVAL",
                "TEST",
                0
        );

        List<QueryPlan.QueryVariant> variants = refiner.refine(
                plan,
                assessment,
                Set.of(refiner.fingerprint("比较 BM25 与向量召回"))
        );

        assertEquals(1, variants.size());
        assertEquals("向量召回", variants.get(0).query());
        assertTrue(variants.stream().noneMatch(item -> item.query().equals(plan.originalQuery())));
    }
}

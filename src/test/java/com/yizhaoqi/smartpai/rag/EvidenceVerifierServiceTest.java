package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EvidenceVerifierServiceTest {

    private final AgenticRagProperties properties = new AgenticRagProperties();
    private final EvidenceVerifierService service = new EvidenceVerifierService(properties);

    @Test
    void returnsInsufficientWhenNoEvidenceExists() {
        EvidenceAssessment result = service.assess(simplePlan("RRF 如何融合结果"), List.of(), 0);

        assertEquals(EvidenceAssessment.Status.INSUFFICIENT, result.status());
        assertEquals("REFINE_RETRIEVAL", result.suggestedAction());
        assertFalse(result.missingAspects().isEmpty());
    }

    @Test
    void requiresEveryMajorAspectForComplexComparison() {
        QueryPlan plan = comparisonPlan();
        SearchResult bm25 = result("doc-a", 1, "BM25 适合关键词、编号和专有名词的精确召回");
        SearchResult unrelated = result("doc-b", 2, "这是另一段不相关的系统说明");

        EvidenceAssessment assessment = service.assess(plan, List.of(bm25, unrelated), 0);

        assertEquals(EvidenceAssessment.Status.PARTIAL, assessment.status());
        assertEquals(List.of("向量召回"), assessment.missingAspects());
    }

    @Test
    void marksComplexEvidenceSufficientAfterMissingAspectIsCovered() {
        QueryPlan plan = comparisonPlan();
        SearchResult bm25 = result("doc-a", 1, "BM25 适合关键词精确召回");
        SearchResult dense = result("doc-b", 2, "向量召回通过 embedding 匹配语义相似内容");

        EvidenceAssessment first = service.assess(plan, List.of(bm25), 0);
        EvidenceAssessment second = service.assess(plan, List.of(bm25, dense), 1);

        assertEquals(EvidenceAssessment.Status.SUFFICIENT, second.status());
        assertEquals("ANSWER", second.suggestedAction());
        assertNotEquals(first.progressSignature(), second.progressSignature());
    }

    private QueryPlan simplePlan(String query) {
        return new QueryPlan(
                query,
                QueryPlan.Intent.KNOWLEDGE_QA,
                QueryPlan.Complexity.SIMPLE,
                true,
                false,
                List.of("RRF"),
                List.of(),
                List.of(new QueryPlan.QueryVariant(QueryPlan.VariantType.ORIGINAL, query, "原始问题")),
                0.9d,
                "TEST"
        );
    }

    private QueryPlan comparisonPlan() {
        String query = "比较 BM25 与向量召回";
        return new QueryPlan(
                query,
                QueryPlan.Intent.COMPARE,
                QueryPlan.Complexity.COMPLEX,
                true,
                false,
                List.of("BM25", "向量召回"),
                List.of(),
                List.of(
                        new QueryPlan.QueryVariant(QueryPlan.VariantType.ORIGINAL, query, "原始问题"),
                        new QueryPlan.QueryVariant(QueryPlan.VariantType.DECOMPOSED, "BM25", "比较对象"),
                        new QueryPlan.QueryVariant(QueryPlan.VariantType.DECOMPOSED, "向量召回", "比较对象")
                ),
                0.9d,
                "TEST"
        );
    }

    private SearchResult result(String fileMd5, int chunkId, String text) {
        return new SearchResult(fileMd5, chunkId, text, 0.9d, "file.md");
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReciprocalRankFusionTest {

    @Test
    void promotesDocumentsSeenByBothChannelsAndPreservesEvidence() {
        SearchResult vectorOnly = result("file-a", 1, 0.99d);
        SearchResult sharedVector = result("file-b", 2, 0.80d);
        SearchResult sharedBm25 = result("file-b", 2, 12.0d);
        SearchResult bm25Only = result("file-c", 3, 11.0d);

        List<SearchResult> fused = ReciprocalRankFusion.fuse(List.of(
                new ReciprocalRankFusion.RankedList("VECTOR", "SEMANTIC", "agent memory", List.of(vectorOnly, sharedVector)),
                new ReciprocalRankFusion.RankedList("BM25", "LEXICAL", "AgentMemory", List.of(sharedBm25, bm25Only))
        ), 3, 60);

        SearchResult shared = fused.stream()
                .filter(item -> "file-b".equals(item.getFileMd5()))
                .findFirst()
                .orElseThrow();
        assertEquals("HYBRID_RRF", shared.getRetrievalMode());
        assertEquals(2, shared.getRetrievalHits().size());
        assertTrue(shared.getRrfScore() > fused.stream()
                .filter(item -> "file-a".equals(item.getFileMd5()))
                .findFirst().orElseThrow().getRrfScore());
    }

    private SearchResult result(String fileMd5, int chunkId, double score) {
        return new SearchResult(fileMd5, chunkId, "content", score);
    }
}

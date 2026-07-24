package com.yizhaoqi.smartpai.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetrievalMetricsTest {

    @Test
    void calculatesRecallMrrAndNdcgAtK() {
        RetrievalMetrics.MetricResult result = RetrievalMetrics.calculate(
                List.of("noise", "doc-a", "doc-b", "doc-c"),
                Set.of("doc-a", "doc-b"),
                3
        );

        assertEquals(1d, result.recallAtK(), 0.0001d);
        assertEquals(0.5d, result.reciprocalRank(), 0.0001d);
        assertEquals(0.6934d, result.ndcgAtK(), 0.001d);
        assertEquals(2, result.relevantHits());
    }

    @Test
    void rejectsCasesWithoutGroundTruth() {
        assertThrows(IllegalArgumentException.class,
                () -> RetrievalMetrics.calculate(List.of("doc-a"), Set.of(), 5));
    }
}

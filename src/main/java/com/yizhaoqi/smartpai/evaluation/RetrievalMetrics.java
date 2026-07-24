package com.yizhaoqi.smartpai.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 与检索实现解耦的离线指标计算，便于在 CI 中对候选排序做确定性回归测试。 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    public static MetricResult calculate(List<String> rankedDocumentIds,
                                         Set<String> relevantDocumentIds,
                                         int topK) {
        if (relevantDocumentIds == null || relevantDocumentIds.isEmpty()) {
            throw new IllegalArgumentException("relevantDocumentIds 不能为空");
        }
        int effectiveK = Math.max(1, topK);
        List<String> ranked = rankedDocumentIds == null
                ? List.of()
                : rankedDocumentIds.stream().limit(effectiveK).toList();
        Set<String> seenRelevant = new HashSet<>();
        double reciprocalRank = 0d;
        double dcg = 0d;
        for (int index = 0; index < ranked.size(); index++) {
            String documentId = ranked.get(index);
            if (relevantDocumentIds.contains(documentId) && seenRelevant.add(documentId)) {
                if (reciprocalRank == 0d) {
                    reciprocalRank = 1d / (index + 1d);
                }
                dcg += 1d / log2(index + 2d);
            }
        }
        double recall = (double) seenRelevant.size() / relevantDocumentIds.size();
        int idealHits = Math.min(effectiveK, relevantDocumentIds.size());
        double idealDcg = 0d;
        for (int index = 0; index < idealHits; index++) {
            idealDcg += 1d / log2(index + 2d);
        }
        double ndcg = idealDcg == 0d ? 0d : dcg / idealDcg;
        return new MetricResult(recall, reciprocalRank, ndcg, seenRelevant.size());
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2d);
    }

    public record MetricResult(double recallAtK, double reciprocalRank, double ndcgAtK, int relevantHits) {
    }
}

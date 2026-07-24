package com.yizhaoqi.smartpai.evaluation;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.rag.AgenticRetrievalService;
import com.yizhaoqi.smartpai.rag.model.RetrievalOutcome;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RetrievalEvaluationService {

    private final AgenticRetrievalService retrievalService;
    private final AgenticRagProperties properties;

    public RetrievalEvaluationService(AgenticRetrievalService retrievalService,
                                      AgenticRagProperties properties) {
        this.retrievalService = retrievalService;
        this.properties = properties;
    }

    public EvaluationReport evaluate(String userId, List<EvaluationCase> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("评测用例不能为空");
        }
        List<CaseResult> results = new ArrayList<>();
        for (EvaluationCase testCase : cases) {
            validate(testCase);
            int topK = Math.max(1, testCase.topK());
            RetrievalOutcome outcome = retrievalService.retrieve(testCase.query(), userId, topK);
            List<String> rankedIds = outcome.results().stream()
                    .map(this::documentId)
                    .toList();
            Set<String> relevant = Set.copyOf(testCase.relevantDocumentIds());
            RetrievalMetrics.MetricResult metrics = RetrievalMetrics.calculate(rankedIds, relevant, topK);
            results.add(new CaseResult(
                    testCase.id(),
                    testCase.query(),
                    rankedIds,
                    metrics.recallAtK(),
                    metrics.reciprocalRank(),
                    metrics.ndcgAtK(),
                    outcome.trace().totalLatencyMs(),
                    outcome.trace().degradations()
            ));
        }

        double recall = average(results.stream().map(CaseResult::recallAtK).toList());
        double mrr = average(results.stream().map(CaseResult::reciprocalRank).toList());
        double ndcg = average(results.stream().map(CaseResult::ndcgAtK).toList());
        double zeroRecallRate = results.stream().filter(item -> item.recallAtK() == 0d).count() / (double) results.size();
        long p95Latency = percentile(results.stream().map(CaseResult::latencyMs).sorted().toList(), 0.95d);
        Gate gate = gate(recall, mrr, ndcg, zeroRecallRate, p95Latency);
        return new EvaluationReport(
                LocalDateTime.now().toString(),
                results.size(),
                recall,
                mrr,
                ndcg,
                zeroRecallRate,
                p95Latency,
                gate,
                List.copyOf(results)
        );
    }

    private Gate gate(double recall, double mrr, double ndcg, double zeroRecallRate, long p95Latency) {
        AgenticRagProperties.Evaluation thresholds = properties.getEvaluation();
        Map<String, GateCheck> checks = new LinkedHashMap<>();
        checks.put("recallAtK", minimum(recall, thresholds.getMinRecallAtK()));
        checks.put("mrr", minimum(mrr, thresholds.getMinMrr()));
        checks.put("ndcgAtK", minimum(ndcg, thresholds.getMinNdcgAtK()));
        checks.put("zeroRecallRate", maximum(zeroRecallRate, thresholds.getMaxZeroRecallRate()));
        checks.put("p95LatencyMs", maximum(p95Latency, thresholds.getMaxP95LatencyMs()));
        boolean passed = checks.values().stream().allMatch(GateCheck::passed);
        return new Gate(passed, checks);
    }

    private GateCheck minimum(double actual, double threshold) {
        return new GateCheck(actual >= threshold, actual, threshold, ">=");
    }

    private GateCheck maximum(double actual, double threshold) {
        return new GateCheck(actual <= threshold, actual, threshold, "<=");
    }

    private String documentId(SearchResult result) {
        return result.getFileMd5() + ":" + result.getChunkId();
    }

    private void validate(EvaluationCase testCase) {
        if (testCase == null || testCase.query() == null || testCase.query().isBlank()) {
            throw new IllegalArgumentException("评测 query 不能为空");
        }
        if (testCase.relevantDocumentIds() == null || testCase.relevantDocumentIds().isEmpty()) {
            throw new IllegalArgumentException("relevantDocumentIds 不能为空");
        }
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    public record EvaluationCase(String id, String query, List<String> relevantDocumentIds, int topK) {
    }

    public record CaseResult(String id,
                             String query,
                             List<String> rankedDocumentIds,
                             double recallAtK,
                             double reciprocalRank,
                             double ndcgAtK,
                             long latencyMs,
                             List<String> degradations) {
    }

    public record Gate(boolean passed, Map<String, GateCheck> checks) {
    }

    public record GateCheck(boolean passed, double actual, double threshold, String operator) {
    }

    public record EvaluationReport(String evaluatedAt,
                                   int caseCount,
                                   double recallAtK,
                                   double mrr,
                                   double ndcgAtK,
                                   double zeroRecallRate,
                                   long p95LatencyMs,
                                   Gate gate,
                                   List<CaseResult> cases) {
    }
}

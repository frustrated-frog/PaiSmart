package com.yizhaoqi.smartpai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RerankerService {

    private static final Logger logger = LoggerFactory.getLogger(RerankerService.class);

    private final AgenticRagProperties properties;
    private final ObjectMapper objectMapper;

    public RerankerService(AgenticRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public RerankOutcome rerank(String query, List<SearchResult> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return new RerankOutcome(List.of(), "EMPTY", null);
        }
        int limit = Math.min(candidates.size(), properties.getReranker().getMaxDocuments());
        List<SearchResult> input = new ArrayList<>(candidates.subList(0, limit));
        if (!properties.getReranker().isEnabled()) {
            return new RerankOutcome(rankByRrf(input, topN), "DISABLED_RRF", null);
        }

        String endpoint = properties.getReranker().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            try {
                return new RerankOutcome(callCrossEncoder(query, input, topN), "CROSS_ENCODER_HTTP", null);
            } catch (Exception exception) {
                logger.warn("Cross-encoder reranker 调用失败，使用轻量本地重排: {}", exception.getMessage());
                return new RerankOutcome(heuristicRerank(query, input, topN), "HEURISTIC_FALLBACK", exception.getMessage());
            }
        }
        return new RerankOutcome(heuristicRerank(query, input, topN), "HEURISTIC_LOCAL", null);
    }

    private List<SearchResult> callCrossEncoder(String query,
                                                List<SearchResult> candidates,
                                                int topN) throws Exception {
        WebClient.Builder builder = WebClient.builder().baseUrl(properties.getReranker().getEndpoint());
        if (properties.getReranker().getApiKey() != null && !properties.getReranker().getApiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getReranker().getApiKey());
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getReranker().getModel());
        request.put("query", query);
        request.put("documents", candidates.stream().map(SearchResult::getTextContent).toList());
        request.put("top_n", Math.min(topN, candidates.size()));

        String response = builder.build().post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(properties.getReranker().getTimeoutSeconds()));
        JsonNode root = objectMapper.readTree(response);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            throw new IllegalStateException("reranker 响应缺少 results 数组");
        }

        List<SearchResult> ranked = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            SearchResult result = candidates.get(index);
            double score = item.has("relevance_score")
                    ? item.path("relevance_score").asDouble()
                    : item.path("score").asDouble();
            result.setRerankScore(score);
            result.setScore(score);
            ranked.add(result);
        }
        if (ranked.isEmpty()) {
            throw new IllegalStateException("reranker 未返回有效候选");
        }
        applyFinalRanks(ranked);
        return ranked.stream().limit(topN).toList();
    }

    private List<SearchResult> heuristicRerank(String query, List<SearchResult> candidates, int topN) {
        Set<String> queryTerms = terms(query);
        for (SearchResult result : candidates) {
            Set<String> documentTerms = terms(result.getTextContent());
            long overlap = queryTerms.stream().filter(documentTerms::contains).count();
            double lexicalCoverage = queryTerms.isEmpty() ? 0d : (double) overlap / queryTerms.size();
            double rrf = result.getRrfScore() == null ? 0d : result.getRrfScore();
            double score = lexicalCoverage * 0.7d + normalizeRrf(rrf) * 0.3d;
            result.setRerankScore(score);
            result.setScore(score);
        }
        List<SearchResult> ranked = candidates.stream()
                .sorted(Comparator.comparing(SearchResult::getRerankScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SearchResult::getRrfScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topN)
                .toList();
        applyFinalRanks(ranked);
        return ranked;
    }

    private List<SearchResult> rankByRrf(List<SearchResult> candidates, int topN) {
        List<SearchResult> ranked = candidates.stream().limit(topN).toList();
        applyFinalRanks(ranked);
        return ranked;
    }

    private void applyFinalRanks(List<SearchResult> results) {
        for (int index = 0; index < results.size(); index++) {
            results.get(index).setFinalRank(index + 1);
        }
    }

    private double normalizeRrf(double score) {
        return Math.min(1d, Math.max(0d, score * 30d));
    }

    private Set<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9一-龥]+"))
                .filter(term -> term.length() > 1)
                .collect(Collectors.toSet());
    }

    public record RerankOutcome(
            List<SearchResult> results,
            String strategy,
            String degradationReason
    ) {
    }
}

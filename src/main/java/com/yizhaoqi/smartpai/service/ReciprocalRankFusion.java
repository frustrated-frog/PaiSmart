package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) 将不同量纲的 BM25 分数和向量相似度按排名融合。
 * 相比直接对原始 score 加权，它不依赖两种检索分数的数值分布，更适合跨模型与跨索引配置。
 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_RANK_CONSTANT = 60;

    private ReciprocalRankFusion() {
    }

    public static List<SearchResult> fuse(List<SearchResult> vectorResults,
                                          List<SearchResult> bm25Results,
                                          int topK) {
        return fuse(List.of(
                new RankedList("VECTOR", "ORIGINAL", null, vectorResults),
                new RankedList("BM25", "ORIGINAL", null, bm25Results)
        ), topK, DEFAULT_RANK_CONSTANT);
    }

    public static List<SearchResult> fuse(List<RankedList> rankedLists,
                                          int topK,
                                          int rankConstant) {
        if (topK <= 0) {
            return List.of();
        }
        if (rankConstant < 1) {
            throw new IllegalArgumentException("rankConstant 必须大于 0");
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        if (rankedLists != null) {
            rankedLists.stream()
                    .filter(list -> list != null && list.results() != null)
                    .forEach(list -> accumulate(candidates, list, rankConstant));
        }

        List<SearchResult> fused = candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(candidate -> candidate.result().getFileMd5(), Comparator.nullsLast(String::compareTo))
                        .thenComparing(candidate -> candidate.result().getChunkId(), Comparator.nullsLast(Integer::compareTo)))
                .limit(topK)
                .map(Candidate::toSearchResult)
                .toList();
        for (int index = 0; index < fused.size(); index++) {
            fused.get(index).setFinalRank(index + 1);
        }
        return fused;
    }

    private static void accumulate(Map<String, Candidate> candidates,
                                   RankedList rankedList,
                                   int rankConstant) {
        List<SearchResult> results = rankedList.results();
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            if (result == null) {
                continue;
            }
            String key = documentKey(result);
            Candidate candidate = candidates.computeIfAbsent(key, ignored -> new Candidate(copy(result)));
            double contribution = 1.0d / (rankConstant + index + 1);
            candidate.add(rankedList, index + 1, result.getScore(), contribution);
        }
    }

    private static String documentKey(SearchResult result) {
        return String.valueOf(result.getFileMd5()) + ':' + String.valueOf(result.getChunkId());
    }

    private static SearchResult copy(SearchResult source) {
        SearchResult copy = new SearchResult(
                source.getFileMd5(),
                source.getChunkId(),
                source.getTextContent(),
                source.getScore(),
                source.getUserId(),
                source.getOrgTag(),
                Boolean.TRUE.equals(source.getIsPublic()),
                source.getFileName(),
                source.getPageNumber(),
                source.getAnchorText(),
                source.getRetrievalMode(),
                source.getMatchedChunkText()
        );
        copy.setParentChunkId(source.getParentChunkId());
        copy.setParentChunkIndex(source.getParentChunkIndex());
        copy.setRawScore(source.getRawScore());
        return copy;
    }

    public record RankedList(
            String channel,
            String queryType,
            String query,
            List<SearchResult> results
    ) {
    }

    private static final class Candidate {
        private final SearchResult result;
        private final List<String> channels = new ArrayList<>(2);
        private double score;

        private Candidate(SearchResult result) {
            this.result = result;
        }

        private void add(RankedList rankedList,
                         int rank,
                         Double rawScore,
                         double contribution) {
            score += contribution;
            String channel = rankedList.channel() == null ? "UNKNOWN" : rankedList.channel();
            if (!channels.contains(channel)) {
                channels.add(channel);
            }
            result.getRetrievalHits().add(new SearchResult.RetrievalHit(
                    channel,
                    rankedList.queryType(),
                    rankedList.query(),
                    rank,
                    rawScore,
                    contribution
            ));
        }

        private double score() {
            return score;
        }

        private SearchResult result() {
            return result;
        }

        private SearchResult toSearchResult() {
            result.setScore(score);
            result.setRrfScore(score);
            if (channels.contains("VECTOR") && channels.contains("BM25")) {
                result.setRetrievalMode("HYBRID_RRF");
            } else if (channels.contains("VECTOR")) {
                result.setRetrievalMode("VECTOR_ONLY");
            } else {
                result.setRetrievalMode("BM25_ONLY");
            }
            return result;
        }
    }
}

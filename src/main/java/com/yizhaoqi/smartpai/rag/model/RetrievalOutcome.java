package com.yizhaoqi.smartpai.rag.model;

import com.yizhaoqi.smartpai.entity.SearchResult;

import java.util.List;

public record RetrievalOutcome(
        List<SearchResult> results,
        RetrievalTrace trace,
        EvidenceAssessment evidenceAssessment,
        int refinementRounds
) {
    public RetrievalOutcome {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public RetrievalOutcome(List<SearchResult> results, RetrievalTrace trace) {
        this(results, trace, null, 0);
    }
}

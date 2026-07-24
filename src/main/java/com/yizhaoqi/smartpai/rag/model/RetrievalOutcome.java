package com.yizhaoqi.smartpai.rag.model;

import com.yizhaoqi.smartpai.entity.SearchResult;

import java.util.List;

public record RetrievalOutcome(
        List<SearchResult> results,
        RetrievalTrace trace
) {
    public RetrievalOutcome {
        results = results == null ? List.of() : List.copyOf(results);
    }
}

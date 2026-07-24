package com.yizhaoqi.smartpai.rag.model;

import java.util.List;

public record QueryPlan(
        String originalQuery,
        Intent intent,
        Complexity complexity,
        boolean retrievalRequired,
        boolean clarificationRequired,
        List<String> entities,
        List<String> constraints,
        List<QueryVariant> variants,
        double confidence,
        String planner
) {
    public QueryPlan {
        entities = entities == null ? List.of() : List.copyOf(entities);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public enum Intent {
        CHAT,
        KNOWLEDGE_QA,
        SUMMARY,
        COMPARE,
        MULTI_HOP,
        ACTION
    }

    public enum Complexity {
        SIMPLE,
        COMPLEX
    }

    public enum VariantType {
        ORIGINAL,
        LEXICAL,
        SEMANTIC,
        DECOMPOSED
    }

    public record QueryVariant(
            VariantType type,
            String query,
            String purpose
    ) {
    }
}

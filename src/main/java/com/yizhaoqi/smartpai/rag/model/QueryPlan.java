package com.yizhaoqi.smartpai.rag.model;

import java.util.List;
import java.util.Map;

public record QueryPlan(
        String originalQuery,
        Intent intent,
        Complexity complexity,
        boolean retrievalRequired,
        boolean clarificationRequired,
        List<String> entities,
        List<String> constraints,
        Map<String, String> knownSlots,
        List<String> missingSlots,
        String clarificationQuestion,
        List<String> clarificationOptions,
        List<QueryVariant> variants,
        double confidence,
        String planner
) {
    public QueryPlan {
        entities = entities == null ? List.of() : List.copyOf(entities);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        knownSlots = knownSlots == null ? Map.of() : Map.copyOf(knownSlots);
        missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
        clarificationOptions = clarificationOptions == null ? List.of() : List.copyOf(clarificationOptions);
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public QueryPlan(String originalQuery,
                     Intent intent,
                     Complexity complexity,
                     boolean retrievalRequired,
                     boolean clarificationRequired,
                     List<String> entities,
                     List<String> constraints,
                     List<QueryVariant> variants,
                     double confidence,
                     String planner) {
        this(originalQuery, intent, complexity, retrievalRequired, clarificationRequired,
                entities, constraints, Map.of(), List.of(), null, List.of(), variants, confidence, planner);
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

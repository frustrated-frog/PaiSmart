package com.yizhaoqi.smartpai.rag.model;

import java.util.List;
import java.util.Map;

public record RetrievalTrace(
        String traceId,
        QueryPlan queryPlan,
        List<Stage> stages,
        List<String> degradations,
        long totalLatencyMs
) {
    public RetrievalTrace {
        stages = stages == null ? List.of() : List.copyOf(stages);
        degradations = degradations == null ? List.of() : List.copyOf(degradations);
    }

    public record Stage(
            String name,
            String status,
            long latencyMs,
            int inputCount,
            int outputCount,
            Map<String, Object> details
    ) {
        public Stage {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }
}

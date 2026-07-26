package com.yizhaoqi.smartpai.model;

import java.util.List;

public record AgentEvaluationDataset(String datasetId, String version, List<AgentEvaluationCase> cases) {
    public AgentEvaluationDataset {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}

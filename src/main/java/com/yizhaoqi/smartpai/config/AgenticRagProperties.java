package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agentic-rag")
public class AgenticRagProperties {

    private boolean enabled = true;
    private QueryPlanning queryPlanning = new QueryPlanning();
    private Retrieval retrieval = new Retrieval();
    private Reranker reranker = new Reranker();
    private Evaluation evaluation = new Evaluation();

    @Data
    public static class QueryPlanning {
        private boolean llmEnabled = true;
        private int maxVariants = 4;
        private int maxCompletionTokens = 600;
    }

    @Data
    public static class Retrieval {
        private int perChannelTopK = 30;
        private int fusionTopK = 40;
        private int finalTopK = 8;
        private int rrfRankConstant = 60;
        private int maxQueryVariants = 4;
    }

    @Data
    public static class Reranker {
        private boolean enabled = true;
        private String endpoint;
        private String apiKey;
        private String model = "bge-reranker-v2-m3";
        private int timeoutSeconds = 12;
        private int maxDocuments = 40;
    }

    @Data
    public static class Evaluation {
        private double minRecallAtK = 0.75d;
        private double minMrr = 0.60d;
        private double minNdcgAtK = 0.65d;
        private double maxZeroRecallRate = 0.15d;
        private long maxP95LatencyMs = 3000L;
    }
}

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
    private Evidence evidence = new Evidence();
    private Evaluation evaluation = new Evaluation();
    private Tools tools = new Tools();
    private Context context = new Context();
    private LoopGuard loopGuard = new LoopGuard();

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
    public static class Evidence {
        private boolean enabled = true;
        private int minResultCount = 2;
        private double queryCoverageThreshold = 0.25d;
        private double aspectCoverageThreshold = 0.5d;
        private double complexAspectCoverageThreshold = 0.75d;
        private int maxRefinementRounds = 2;
        private int maxRefinementQueries = 3;
    }

    @Data
    public static class Evaluation {
        private double minRecallAtK = 0.75d;
        private double minMrr = 0.60d;
        private double minNdcgAtK = 0.65d;
        private double maxZeroRecallRate = 0.15d;
        private long maxP95LatencyMs = 3000L;
        private double minClarificationF1 = 0.80d;
        private double minIntentMacroF1 = 0.80d;
        private double minEvidenceStatusMacroF1 = 0.80d;
        private double minConflictDetectionF1 = 0.80d;
        private double minTrajectoryScore = 0.80d;
        private double maxInvalidTransitionRate = 0.05d;
        private double minToolSelectionF1 = 0.80d;
        private double minToolArgumentAccuracy = 0.80d;
        private double minCitationF1 = 0.80d;
        private double minClaimCoverage = 0.80d;
        private double maxDuplicateActionRate = 0.05d;
        private double minTerminalReasonAccuracy = 0.90d;
        private double minResumeSuccessRate = 0.80d;
        private double maxDuplicateSideEffectRate = 0D;
        private double minPassPowerK = 0.70d;
    }

    @Data
    public static class Tools {
        private int timeoutSeconds = 90;
        private int maxConcurrentPerUser = 2;
        private int circuitFailureThreshold = 3;
        private int circuitCooldownSeconds = 30;
    }

    @Data
    public static class Context {
        private int maxPromptChars = 32000;
        private int maxToolObservationChars = 8000;
        private int maxHistoricalMessageChars = 1600;
    }

    @Data
    public static class LoopGuard {
        private int repeatWarningThreshold = 2;
        private int repeatHardLimit = 3;
        private int noProgressLimit = 2;
    }
}

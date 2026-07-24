package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.rag.model.RetrievalOutcome;
import com.yizhaoqi.smartpai.rag.model.RetrievalTrace;
import com.yizhaoqi.smartpai.service.HybridSearchService;
import com.yizhaoqi.smartpai.service.ReciprocalRankFusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class AgenticRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(AgenticRetrievalService.class);

    private final QueryPlanningService queryPlanningService;
    private final HybridSearchService hybridSearchService;
    private final RerankerService rerankerService;
    private final ParentContextAssembler parentContextAssembler;
    private final EvidenceVerifierService evidenceVerifierService;
    private final CorrectiveQueryRefiner correctiveQueryRefiner;
    private final AgenticRagProperties properties;
    private final Executor retrievalExecutor;

    public AgenticRetrievalService(QueryPlanningService queryPlanningService,
                                   HybridSearchService hybridSearchService,
                                   RerankerService rerankerService,
                                   ParentContextAssembler parentContextAssembler,
                                   EvidenceVerifierService evidenceVerifierService,
                                   CorrectiveQueryRefiner correctiveQueryRefiner,
                                   AgenticRagProperties properties,
                                   @Qualifier("ragRetrievalExecutor") Executor retrievalExecutor) {
        this.queryPlanningService = queryPlanningService;
        this.hybridSearchService = hybridSearchService;
        this.rerankerService = rerankerService;
        this.parentContextAssembler = parentContextAssembler;
        this.evidenceVerifierService = evidenceVerifierService;
        this.correctiveQueryRefiner = correctiveQueryRefiner;
        this.properties = properties;
        this.retrievalExecutor = retrievalExecutor;
    }

    public RetrievalOutcome retrieve(String query, String userId, int requestedTopK) {
        long totalStartedAt = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        List<RetrievalTrace.Stage> stages = new ArrayList<>();
        List<String> degradations = Collections.synchronizedList(new ArrayList<>());

        long planningStartedAt = System.currentTimeMillis();
        QueryPlan plan = queryPlanningService.plan(query, userId);
        stages.add(new RetrievalTrace.Stage(
                "QUERY_PLANNING",
                "SUCCEEDED",
                elapsed(planningStartedAt),
                1,
                plan.variants().size(),
                Map.of(
                        "intent", plan.intent().name(),
                        "complexity", plan.complexity().name(),
                        "planner", plan.planner(),
                        "confidence", plan.confidence(),
                        "clarificationRequired", plan.clarificationRequired()
                )
        ));

        if (!properties.isEnabled() || !plan.retrievalRequired()) {
            RetrievalTrace trace = new RetrievalTrace(traceId, plan, stages, degradations, elapsed(totalStartedAt));
            return new RetrievalOutcome(List.of(), trace, null, 0);
        }

        AgenticRagProperties.Retrieval retrieval = properties.getRetrieval();
        int perChannelTopK = Math.max(requestedTopK, retrieval.getPerChannelTopK());
        int finalTopK = Math.max(1, requestedTopK > 0 ? requestedTopK : retrieval.getFinalTopK());
        List<QueryPlan.QueryVariant> initialVariants = plan.variants().stream()
                .limit(retrieval.getMaxQueryVariants())
                .toList();

        Set<String> executedQueries = new LinkedHashSet<>();
        initialVariants.forEach(variant -> executedQueries.add(correctiveQueryRefiner.fingerprint(variant.query())));
        List<ReciprocalRankFusion.RankedList> rankedLists = new ArrayList<>();
        rankedLists.addAll(executeRecallRound(initialVariants, userId, perChannelTopK, 0, stages, degradations));

        RankingOutcome ranking = rankAndAssemble(plan.originalQuery(), rankedLists, finalTopK, 0, stages, degradations);
        EvidenceAssessment assessment = assess(plan, ranking.results(), 0, stages);
        int completedRefinementRounds = 0;
        String previousSignature = assessment.progressSignature();

        if (properties.getEvidence().isEnabled()) {
            for (int round = 1; round <= properties.getEvidence().getMaxRefinementRounds(); round++) {
                if (!needsRefinement(assessment)) {
                    break;
                }
                List<QueryPlan.QueryVariant> refinedVariants = correctiveQueryRefiner.refine(plan, assessment, executedQueries);
                if (refinedVariants.isEmpty()) {
                    degradations.add("EVIDENCE_REFINE_SKIPPED: 没有新的差异化查询");
                    break;
                }
                refinedVariants.forEach(variant -> executedQueries.add(correctiveQueryRefiner.fingerprint(variant.query())));
                rankedLists.addAll(executeRecallRound(refinedVariants, userId, perChannelTopK, round, stages, degradations));
                ranking = rankAndAssemble(plan.originalQuery(), rankedLists, finalTopK, round, stages, degradations);
                assessment = assess(plan, ranking.results(), round, stages);
                completedRefinementRounds = round;
                if (assessment.progressSignature().equals(previousSignature)) {
                    degradations.add("EVIDENCE_REFINE_STOPPED: 连续两轮证据集合与覆盖主题没有变化");
                    break;
                }
                previousSignature = assessment.progressSignature();
            }
        }

        RetrievalTrace trace = new RetrievalTrace(
                traceId,
                plan,
                stages,
                List.copyOf(degradations),
                elapsed(totalStartedAt)
        );
        logger.info("Agentic retrieval 完成: traceId={}, variants={}, lists={}, final={}, evidence={}, refinementRounds={}, latencyMs={}, degradations={}",
                traceId,
                executedQueries.size(),
                rankedLists.size(),
                ranking.results().size(),
                assessment.status(),
                completedRefinementRounds,
                trace.totalLatencyMs(),
                degradations.size());
        return new RetrievalOutcome(ranking.results(), trace, assessment, completedRefinementRounds);
    }

    private List<ReciprocalRankFusion.RankedList> executeRecallRound(List<QueryPlan.QueryVariant> variants,
                                                                     String userId,
                                                                     int topK,
                                                                     int round,
                                                                     List<RetrievalTrace.Stage> stages,
                                                                     List<String> degradations) {
        long startedAt = System.currentTimeMillis();
        List<CompletableFuture<ChannelExecution>> futures = new ArrayList<>();
        for (QueryPlan.QueryVariant variant : variants) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeChannel("BM25", variant, userId, topK), retrievalExecutor));
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeChannel("VECTOR", variant, userId, topK), retrievalExecutor));
        }

        List<ReciprocalRankFusion.RankedList> rankedLists = new ArrayList<>();
        Map<String, Object> channelCounts = new LinkedHashMap<>();
        int degradationCountBefore = degradations.size();
        for (CompletableFuture<ChannelExecution> future : futures) {
            ChannelExecution execution;
            try {
                execution = future.join();
            } catch (Exception exception) {
                degradations.add("RETRIEVAL_TASK_FAILED: " + rootMessage(exception));
                continue;
            }
            String countKey = execution.channel() + ':' + execution.variant().type() + ':' + round;
            channelCounts.put(countKey, execution.results().size());
            if (execution.error() != null) {
                degradations.add(countKey + " -> " + execution.error());
            }
            rankedLists.add(new ReciprocalRankFusion.RankedList(
                    execution.channel(),
                    execution.variant().type().name(),
                    execution.variant().query(),
                    execution.results()
            ));
        }
        int recalledCount = rankedLists.stream().mapToInt(list -> list.results().size()).sum();
        stages.add(new RetrievalTrace.Stage(
                round == 0 ? "PARALLEL_RECALL" : "CORRECTIVE_RECALL_" + round,
                degradations.size() == degradationCountBefore ? "SUCCEEDED" : "DEGRADED",
                elapsed(startedAt),
                variants.size(),
                recalledCount,
                channelCounts
        ));
        return rankedLists;
    }

    private RankingOutcome rankAndAssemble(String query,
                                           List<ReciprocalRankFusion.RankedList> rankedLists,
                                           int finalTopK,
                                           int round,
                                           List<RetrievalTrace.Stage> stages,
                                           List<String> degradations) {
        AgenticRagProperties.Retrieval retrieval = properties.getRetrieval();
        String suffix = round == 0 ? "" : "_" + round;
        int recalledCount = rankedLists.stream().mapToInt(list -> list.results().size()).sum();

        long fusionStartedAt = System.currentTimeMillis();
        List<SearchResult> fused = ReciprocalRankFusion.fuse(
                rankedLists,
                retrieval.getFusionTopK(),
                retrieval.getRrfRankConstant()
        );
        stages.add(new RetrievalTrace.Stage(
                "RRF_FUSION" + suffix,
                "SUCCEEDED",
                elapsed(fusionStartedAt),
                recalledCount,
                fused.size(),
                Map.of("rankConstant", retrieval.getRrfRankConstant(), "refinementRound", round)
        ));

        long rerankStartedAt = System.currentTimeMillis();
        RerankerService.RerankOutcome rerankOutcome = rerankerService.rerank(query, fused, finalTopK);
        if (rerankOutcome.degradationReason() != null) {
            degradations.add("RERANKER" + suffix + " -> " + rerankOutcome.degradationReason());
        }
        stages.add(new RetrievalTrace.Stage(
                "RERANK" + suffix,
                rerankOutcome.degradationReason() == null ? "SUCCEEDED" : "DEGRADED",
                elapsed(rerankStartedAt),
                fused.size(),
                rerankOutcome.results().size(),
                Map.of("strategy", rerankOutcome.strategy(), "refinementRound", round)
        ));

        long assemblyStartedAt = System.currentTimeMillis();
        ParentContextAssembler.AssemblyOutcome assembly = parentContextAssembler.assemble(
                rerankOutcome.results(),
                finalTopK
        );
        stages.add(new RetrievalTrace.Stage(
                "PARENT_CONTEXT_ASSEMBLY" + suffix,
                "SUCCEEDED",
                elapsed(assemblyStartedAt),
                rerankOutcome.results().size(),
                assembly.results().size(),
                Map.of(
                        "expandedParents", assembly.expandedCount(),
                        "deduplicatedChildren", assembly.deduplicatedCount(),
                        "refinementRound", round
                )
        ));
        return new RankingOutcome(assembly.results());
    }

    private EvidenceAssessment assess(QueryPlan plan,
                                      List<SearchResult> results,
                                      int round,
                                      List<RetrievalTrace.Stage> stages) {
        long startedAt = System.currentTimeMillis();
        EvidenceAssessment assessment = evidenceVerifierService.assess(plan, results, round);
        stages.add(new RetrievalTrace.Stage(
                round == 0 ? "EVIDENCE_VERIFY" : "EVIDENCE_VERIFY_" + round,
                assessment.status().name(),
                elapsed(startedAt),
                results.size(),
                assessment.coveredAspects().size(),
                Map.of(
                        "confidence", assessment.confidence(),
                        "coveredAspects", assessment.coveredAspects(),
                        "missingAspects", assessment.missingAspects(),
                        "conflictCount", assessment.conflicts().size(),
                        "suggestedAction", assessment.suggestedAction(),
                        "progressSignature", assessment.progressSignature(),
                        "refinementRound", round
                )
        ));
        return assessment;
    }

    private boolean needsRefinement(EvidenceAssessment assessment) {
        return assessment.status() == EvidenceAssessment.Status.PARTIAL
                || assessment.status() == EvidenceAssessment.Status.INSUFFICIENT;
    }

    private ChannelExecution executeChannel(String channel,
                                            QueryPlan.QueryVariant variant,
                                            String userId,
                                            int topK) {
        try {
            List<SearchResult> results = "BM25".equals(channel)
                    ? hybridSearchService.searchBm25WithPermission(variant.query(), userId, topK)
                    : hybridSearchService.searchVectorWithPermission(variant.query(), userId, topK);
            return new ChannelExecution(channel, variant, results, null);
        } catch (Exception exception) {
            logger.warn("检索通道失败: channel={}, queryType={}, reason={}",
                    channel, variant.type(), exception.getMessage());
            return new ChannelExecution(channel, variant, List.of(), rootMessage(exception));
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ChannelExecution(
            String channel,
            QueryPlan.QueryVariant variant,
            List<SearchResult> results,
            String error
    ) {
    }

    private record RankingOutcome(List<SearchResult> results) {
    }
}

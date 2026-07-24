package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
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
import java.util.List;
import java.util.Map;
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
    private final AgenticRagProperties properties;
    private final Executor retrievalExecutor;

    public AgenticRetrievalService(QueryPlanningService queryPlanningService,
                                   HybridSearchService hybridSearchService,
                                   RerankerService rerankerService,
                                   ParentContextAssembler parentContextAssembler,
                                   AgenticRagProperties properties,
                                   @Qualifier("ragRetrievalExecutor") Executor retrievalExecutor) {
        this.queryPlanningService = queryPlanningService;
        this.hybridSearchService = hybridSearchService;
        this.rerankerService = rerankerService;
        this.parentContextAssembler = parentContextAssembler;
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
                        "confidence", plan.confidence()
                )
        ));

        if (!properties.isEnabled() || !plan.retrievalRequired()) {
            RetrievalTrace trace = new RetrievalTrace(traceId, plan, stages, degradations, elapsed(totalStartedAt));
            return new RetrievalOutcome(List.of(), trace);
        }

        AgenticRagProperties.Retrieval retrieval = properties.getRetrieval();
        int perChannelTopK = Math.max(requestedTopK, retrieval.getPerChannelTopK());
        int finalTopK = Math.max(1, requestedTopK > 0 ? requestedTopK : retrieval.getFinalTopK());
        List<QueryPlan.QueryVariant> variants = plan.variants().stream()
                .limit(retrieval.getMaxQueryVariants())
                .toList();

        long recallStartedAt = System.currentTimeMillis();
        List<CompletableFuture<ChannelExecution>> futures = new ArrayList<>();
        for (QueryPlan.QueryVariant variant : variants) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeChannel("BM25", variant, userId, perChannelTopK), retrievalExecutor));
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeChannel("VECTOR", variant, userId, perChannelTopK), retrievalExecutor));
        }

        List<ReciprocalRankFusion.RankedList> rankedLists = new ArrayList<>();
        Map<String, Object> channelCounts = new LinkedHashMap<>();
        for (CompletableFuture<ChannelExecution> future : futures) {
            ChannelExecution execution;
            try {
                execution = future.join();
            } catch (Exception exception) {
                degradations.add("RETRIEVAL_TASK_FAILED: " + rootMessage(exception));
                continue;
            }
            String countKey = execution.channel() + ":" + execution.variant().type();
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
                "PARALLEL_RECALL",
                degradations.isEmpty() ? "SUCCEEDED" : "DEGRADED",
                elapsed(recallStartedAt),
                variants.size(),
                recalledCount,
                channelCounts
        ));

        long fusionStartedAt = System.currentTimeMillis();
        List<SearchResult> fused = ReciprocalRankFusion.fuse(
                rankedLists,
                retrieval.getFusionTopK(),
                retrieval.getRrfRankConstant()
        );
        stages.add(new RetrievalTrace.Stage(
                "RRF_FUSION",
                "SUCCEEDED",
                elapsed(fusionStartedAt),
                recalledCount,
                fused.size(),
                Map.of("rankConstant", retrieval.getRrfRankConstant())
        ));

        long rerankStartedAt = System.currentTimeMillis();
        RerankerService.RerankOutcome rerankOutcome = rerankerService.rerank(query, fused, finalTopK);
        if (rerankOutcome.degradationReason() != null) {
            degradations.add("RERANKER -> " + rerankOutcome.degradationReason());
        }
        stages.add(new RetrievalTrace.Stage(
                "RERANK",
                rerankOutcome.degradationReason() == null ? "SUCCEEDED" : "DEGRADED",
                elapsed(rerankStartedAt),
                fused.size(),
                rerankOutcome.results().size(),
                Map.of("strategy", rerankOutcome.strategy())
        ));

        long assemblyStartedAt = System.currentTimeMillis();
        ParentContextAssembler.AssemblyOutcome assembly = parentContextAssembler.assemble(
                rerankOutcome.results(),
                finalTopK
        );
        stages.add(new RetrievalTrace.Stage(
                "PARENT_CONTEXT_ASSEMBLY",
                "SUCCEEDED",
                elapsed(assemblyStartedAt),
                rerankOutcome.results().size(),
                assembly.results().size(),
                Map.of(
                        "expandedParents", assembly.expandedCount(),
                        "deduplicatedChildren", assembly.deduplicatedCount()
                )
        ));

        RetrievalTrace trace = new RetrievalTrace(
                traceId,
                plan,
                stages,
                List.copyOf(degradations),
                elapsed(totalStartedAt)
        );
        logger.info("Agentic retrieval 完成: traceId={}, variants={}, recalled={}, fused={}, final={}, latencyMs={}, degradations={}",
                traceId, variants.size(), recalledCount, fused.size(), assembly.results().size(), trace.totalLatencyMs(), degradations.size());
        return new RetrievalOutcome(assembly.results(), trace);
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
}

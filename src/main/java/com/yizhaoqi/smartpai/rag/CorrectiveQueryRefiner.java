package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 根据证据缺口生成差异化补检索查询，不允许重复已经执行的 query。 */
@Service
public class CorrectiveQueryRefiner {

    private final AgenticRagProperties properties;

    public CorrectiveQueryRefiner(AgenticRagProperties properties) {
        this.properties = properties;
    }

    public List<QueryPlan.QueryVariant> refine(QueryPlan plan,
                                               EvidenceAssessment assessment,
                                               Set<String> executedQueryFingerprints) {
        Map<String, QueryPlan.QueryVariant> candidates = new LinkedHashMap<>();
        for (String missing : assessment.missingAspects()) {
            addCandidate(candidates, new QueryPlan.QueryVariant(
                    QueryPlan.VariantType.DECOMPOSED,
                    missing,
                    "针对未覆盖主题进行纠正检索"
            ));
        }

        if (!assessment.missingAspects().isEmpty()) {
            String combined = String.join(" ", assessment.missingAspects());
            addCandidate(candidates, new QueryPlan.QueryVariant(
                    QueryPlan.VariantType.LEXICAL,
                    combined,
                    "聚合缺失实体和限定词，补充精确召回"
            ));
        }

        if (candidates.isEmpty()) {
            String constrained = String.join(" ", concat(plan.entities(), plan.constraints(), List.of(plan.originalQuery())));
            addCandidate(candidates, new QueryPlan.QueryVariant(
                    QueryPlan.VariantType.SEMANTIC,
                    constrained,
                    "证据覆盖不足，保留原始实体与约束重新召回"
            ));
        }

        return candidates.values().stream()
                .filter(variant -> !executedQueryFingerprints.contains(fingerprint(variant.query())))
                .limit(Math.max(1, properties.getEvidence().getMaxRefinementQueries()))
                .toList();
    }

    public String fingerprint(String query) {
        return query == null ? "" : query.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private void addCandidate(Map<String, QueryPlan.QueryVariant> candidates, QueryPlan.QueryVariant variant) {
        String fingerprint = fingerprint(variant.query());
        if (!fingerprint.isBlank()) {
            candidates.putIfAbsent(fingerprint, variant);
        }
    }

    @SafeVarargs
    private final List<String> concat(List<String>... groups) {
        List<String> values = new ArrayList<>();
        for (List<String> group : groups) {
            if (group != null) {
                group.stream().filter(value -> value != null && !value.isBlank()).forEach(values::add);
            }
        }
        return values;
    }
}

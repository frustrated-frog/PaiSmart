package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 轻量、确定性的证据判断器。它先回答“现有证据覆盖了什么”，再决定是否值得增加一次模型调用。
 */
@Service
public class EvidenceVerifierService {

    private static final Set<String> STOP_WORDS = Set.of(
            "什么", "怎么", "如何", "为什么", "是否", "可以", "一下", "这个", "那个", "相关", "介绍", "请问", "帮我",
            "the", "a", "an", "is", "are", "of", "to", "and", "or", "how", "what", "why"
    );

    private final AgenticRagProperties properties;

    public EvidenceVerifierService(AgenticRagProperties properties) {
        this.properties = properties;
    }

    public EvidenceAssessment assess(QueryPlan plan, List<SearchResult> results, int refinementRound) {
        List<SearchResult> safeResults = results == null ? List.of() : results;
        List<String> aspects = requiredAspects(plan);
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String aspect : aspects) {
            if (bestCoverage(aspect, safeResults) >= properties.getEvidence().getAspectCoverageThreshold()) {
                covered.add(aspect);
            } else {
                missing.add(aspect);
            }
        }

        double queryCoverage = aggregateCoverage(plan.originalQuery(), safeResults);
        double aspectCoverage = aspects.isEmpty() ? queryCoverage : (double) covered.size() / aspects.size();
        List<String> conflicts = detectConservativeConflicts(plan.originalQuery(), safeResults);
        EvidenceAssessment.Status status = decideStatus(plan, safeResults, queryCoverage, aspectCoverage, conflicts);
        double confidence = confidence(status, safeResults.size(), queryCoverage, aspectCoverage);
        String action = switch (status) {
            case SUFFICIENT -> "ANSWER";
            case CONFLICTED -> "ANSWER_WITH_CONFLICTS";
            case PARTIAL, INSUFFICIENT -> refinementRound < properties.getEvidence().getMaxRefinementRounds()
                    ? "REFINE_RETRIEVAL"
                    : (safeResults.isEmpty() ? "ABSTAIN" : "PARTIAL_ANSWER");
        };
        String signature = progressSignature(safeResults, covered, status);
        return new EvidenceAssessment(
                status,
                confidence,
                covered,
                missing,
                conflicts,
                signature,
                action,
                "DETERMINISTIC",
                refinementRound
        );
    }

    private EvidenceAssessment.Status decideStatus(QueryPlan plan,
                                                    List<SearchResult> results,
                                                    double queryCoverage,
                                                    double aspectCoverage,
                                                    List<String> conflicts) {
        if (!conflicts.isEmpty()) {
            return EvidenceAssessment.Status.CONFLICTED;
        }
        if (results.isEmpty()) {
            return EvidenceAssessment.Status.INSUFFICIENT;
        }
        AgenticRagProperties.Evidence config = properties.getEvidence();
        boolean enoughResults = results.size() >= config.getMinResultCount();
        boolean enoughQueryCoverage = queryCoverage >= config.getQueryCoverageThreshold();
        boolean complex = plan.complexity() == QueryPlan.Complexity.COMPLEX
                || plan.intent() == QueryPlan.Intent.COMPARE
                || plan.intent() == QueryPlan.Intent.MULTI_HOP;
        boolean enoughAspects = !complex || aspectCoverage >= config.getComplexAspectCoverageThreshold();
        if (enoughResults && enoughQueryCoverage && enoughAspects) {
            return EvidenceAssessment.Status.SUFFICIENT;
        }
        if (queryCoverage > 0d || aspectCoverage > 0d) {
            return EvidenceAssessment.Status.PARTIAL;
        }
        return EvidenceAssessment.Status.INSUFFICIENT;
    }

    private List<String> requiredAspects(QueryPlan plan) {
        LinkedHashSet<String> aspects = new LinkedHashSet<>();
        plan.variants().stream()
                .filter(variant -> variant.type() == QueryPlan.VariantType.DECOMPOSED)
                .map(QueryPlan.QueryVariant::query)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> aspects.add(value.trim()));
        if (aspects.isEmpty() && (plan.intent() == QueryPlan.Intent.COMPARE || plan.intent() == QueryPlan.Intent.MULTI_HOP)) {
            plan.entities().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .limit(6)
                    .forEach(value -> aspects.add(value.trim()));
        }
        if (aspects.isEmpty() && plan.originalQuery() != null && !plan.originalQuery().isBlank()) {
            aspects.add(plan.originalQuery().trim());
        }
        return List.copyOf(aspects);
    }

    private double bestCoverage(String aspect, List<SearchResult> results) {
        Set<String> expected = terms(aspect);
        if (expected.isEmpty()) {
            return 0d;
        }
        return results.stream()
                .mapToDouble(result -> overlap(expected, terms(searchableText(result))))
                .max()
                .orElse(0d);
    }

    private double aggregateCoverage(String query, List<SearchResult> results) {
        Set<String> expected = terms(query);
        if (expected.isEmpty() || results.isEmpty()) {
            return 0d;
        }
        Set<String> actual = new LinkedHashSet<>();
        results.forEach(result -> actual.addAll(terms(searchableText(result))));
        return overlap(expected, actual);
    }

    private double overlap(Set<String> expected, Set<String> actual) {
        if (expected.isEmpty() || actual.isEmpty()) {
            return 0d;
        }
        long matches = expected.stream().filter(actual::contains).count();
        return (double) matches / expected.size();
    }

    private Set<String> terms(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String normalized = normalize(text);
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                result.add(token);
            }
        }
        String compactChinese = normalized.replaceAll("[^\\p{IsHan}]", "");
        for (int index = 0; index + 1 < compactChinese.length(); index++) {
            String bigram = compactChinese.substring(index, index + 2);
            if (!STOP_WORDS.contains(bigram)) {
                result.add(bigram);
            }
        }
        return result;
    }

    private List<String> detectConservativeConflicts(String query, List<SearchResult> results) {
        if (query == null || !query.contains("是否") || results.size() < 2) {
            return List.of();
        }
        List<String> texts = results.stream().map(this::searchableText).map(this::normalize).toList();
        boolean supports = texts.stream().anyMatch(text -> text.contains("支持") && !text.contains("不支持"));
        boolean rejects = texts.stream().anyMatch(text -> text.contains("不支持") || text.contains("禁止"));
        if (supports && rejects) {
            return List.of("检索证据对“是否支持”给出了相反结论，需要在回答中分别列出来源");
        }
        return List.of();
    }

    private double confidence(EvidenceAssessment.Status status,
                              int resultCount,
                              double queryCoverage,
                              double aspectCoverage) {
        double evidenceVolume = Math.min(1d, resultCount / 4d);
        double base = 0.25d * evidenceVolume + 0.4d * queryCoverage + 0.35d * aspectCoverage;
        if (status == EvidenceAssessment.Status.INSUFFICIENT && resultCount == 0) {
            return 0.95d;
        }
        return Math.max(0d, Math.min(1d, base));
    }

    private String progressSignature(List<SearchResult> results,
                                     List<String> covered,
                                     EvidenceAssessment.Status status) {
        List<String> documentIds = results.stream()
                .map(result -> String.valueOf(result.getFileMd5()) + ':' + result.getChunkId())
                .sorted()
                .toList();
        List<String> sortedCovered = covered.stream().sorted(Comparator.naturalOrder()).toList();
        return sha256(status + "|" + String.join(",", documentIds) + "|" + String.join(",", sortedCovered));
    }

    private String searchableText(SearchResult result) {
        if (result == null) {
            return "";
        }
        return String.join(" ",
                result.getFileName() == null ? "" : result.getFileName(),
                result.getAnchorText() == null ? "" : result.getAnchorText(),
                result.getMatchedChunkText() == null ? "" : result.getMatchedChunkText(),
                result.getTextContent() == null ? "" : result.getTextContent());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) {
                output.append(String.format("%02x", item));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成证据进展签名", exception);
        }
    }
}

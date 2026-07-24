package com.yizhaoqi.smartpai.rag.model;

import java.util.List;

/** 检索证据的可解释判断结果，用于决定回答、补充检索或降级。 */
public record EvidenceAssessment(
        Status status,
        double confidence,
        List<String> coveredAspects,
        List<String> missingAspects,
        List<String> conflicts,
        String progressSignature,
        String suggestedAction,
        String assessmentMode,
        int refinementRound
) {
    public EvidenceAssessment {
        coveredAspects = coveredAspects == null ? List.of() : List.copyOf(coveredAspects);
        missingAspects = missingAspects == null ? List.of() : List.copyOf(missingAspects);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public enum Status {
        SUFFICIENT,
        PARTIAL,
        CONFLICTED,
        INSUFFICIENT
    }
}

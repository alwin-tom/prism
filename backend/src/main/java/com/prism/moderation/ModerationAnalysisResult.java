package com.prism.moderation;

import java.util.List;

public record ModerationAnalysisResult(
        String overallRisk,
        String explanation,
        int critical,
        int high,
        int medium,
        int low,
        boolean clean,
        List<ModerationFindingDraft> findings,
        String modelId,
        String provider
) {
    public ModerationAnalysisResult {
        findings = List.copyOf(findings == null ? List.of() : findings);
        if (overallRisk == null || overallRisk.isBlank()) {
            overallRisk = "CLEAN";
        }
        if (provider == null || provider.isBlank()) {
            provider = "openrouter";
        }
    }
}

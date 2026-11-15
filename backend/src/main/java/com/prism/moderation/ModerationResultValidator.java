package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ModerationResultValidator {

    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> RISKS = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "CLEAN");
    private static final Set<String> CATEGORIES = Set.of(
            "PROFANITY", "HARASSMENT", "HATE_SPEECH", "THREATS", "VIOLENCE",
            "SEXUAL_CONTENT", "BULLYING", "DEFAMATION", "SELF_HARM",
            "ILLEGAL_ACTIVITY", "SCAM_FRAUD", "OTHER"
    );

    public ModerationAnalysisResult validate(JsonNode content, String modelId, String provider) {
        if (content == null || content.isNull() || !content.isObject()) {
            throw new IllegalArgumentException("Moderation response was empty.");
        }
        List<ModerationFindingDraft> findings = new ArrayList<>();
        JsonNode rawFindings = content.path("findings");
        if (rawFindings.isArray()) {
            for (JsonNode row : rawFindings) {
                ModerationFindingDraft draft = finding(row);
                if (draft != null) {
                    findings.add(draft);
                }
            }
        }
        findings = dedupe(findings);
        String overall = normalizeRisk(content.path("overallRisk").asText(""));
        int critical = count(findings, "CRITICAL");
        int high = count(findings, "HIGH");
        int medium = count(findings, "MEDIUM");
        int low = count(findings, "LOW");
        if (content.path("summary").isObject()) {
            critical = Math.max(critical, content.path("summary").path("critical").asInt(critical));
            high = Math.max(high, content.path("summary").path("high").asInt(high));
            medium = Math.max(medium, content.path("summary").path("medium").asInt(medium));
            low = Math.max(low, content.path("summary").path("low").asInt(low));
        }
        if (!findings.isEmpty()) {
            overall = highest(overall, highestSeverity(findings));
        } else {
            overall = "CLEAN";
        }
        boolean clean = findings.isEmpty() && "CLEAN".equals(overall);
        String explanation = text(content.path("explanation"), 2000);
        if (explanation.isBlank()) {
            explanation = clean ? "No concerning textual content was detected." : "Potential policy issues were flagged for human review.";
        }
        return new ModerationAnalysisResult(
                overall,
                explanation,
                critical,
                high,
                medium,
                low,
                clean,
                findings,
                modelId,
                provider
        );
    }

    private ModerationFindingDraft finding(JsonNode row) {
        if (row == null || !row.isObject()) {
            return null;
        }
        String matched = text(row.path("matchedText"), 2000);
        String reason = text(row.path("reason"), 2000);
        String category = normalizeCategory(row.path("category").asText(""));
        String severity = normalizeSeverity(row.path("severity").asText(""));
        if (matched.isBlank() || reason.isBlank() || category == null || severity == null) {
            return null;
        }
        BigDecimal confidence = confidence(row.path("confidence"));
        Integer start = intOrNull(row.path("startOffset"));
        Integer end = intOrNull(row.path("endOffset"));
        if (start != null && end != null && end < start) {
            end = start;
        }
        return new ModerationFindingDraft(
                category,
                severity,
                confidence,
                blankToNull(text(row.path("pageId"), 120)),
                blankToNull(text(row.path("sectionId"), 120)),
                blankToNull(text(row.path("elementId"), 120)),
                blankToNull(text(row.path("elementType"), 40)),
                matched,
                start,
                end,
                blankToNull(text(row.path("context"), 2000)),
                reason,
                blankToNull(text(row.path("suggestion"), 2000))
        );
    }

    private static List<ModerationFindingDraft> dedupe(List<ModerationFindingDraft> findings) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ModerationFindingDraft> unique = new ArrayList<>();
        for (ModerationFindingDraft finding : findings) {
            String key = String.join("|",
                    nullToEmpty(finding.elementId()),
                    nullToEmpty(finding.category()),
                    nullToEmpty(finding.severity()),
                    nullToEmpty(finding.matchedText()).toLowerCase(Locale.ROOT));
            if (seen.add(key)) {
                unique.add(finding);
            }
        }
        return unique;
    }

    private static int count(List<ModerationFindingDraft> findings, String severity) {
        int total = 0;
        for (ModerationFindingDraft finding : findings) {
            if (severity.equals(finding.severity())) {
                total++;
            }
        }
        return total;
    }

    private static String highestSeverity(List<ModerationFindingDraft> findings) {
        String best = "LOW";
        for (ModerationFindingDraft finding : findings) {
            best = highest(best, finding.severity());
        }
        return best;
    }

    static String highest(String left, String right) {
        return rank(left) >= rank(right) ? normalizeRisk(left) : normalizeRisk(right);
    }

    private static int rank(String risk) {
        return switch (normalizeRisk(risk)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String normalizeRisk(String value) {
        String risk = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return RISKS.contains(risk) ? risk : "CLEAN";
    }

    private static String normalizeSeverity(String value) {
        String severity = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return SEVERITIES.contains(severity) ? severity : null;
    }

    private static String normalizeCategory(String value) {
        String category = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return CATEGORIES.contains(category) ? category : null;
    }

    private static BigDecimal confidence(JsonNode node) {
        double value = node.isNumber() ? node.asDouble(0.5) : 0.5;
        if (Double.isNaN(value) || value < 0) {
            value = 0;
        }
        if (value > 1) {
            value = 1;
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return Math.max(0, node.asInt());
    }

    private static String text(JsonNode node, int max) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        String value = node.asText("").trim();
        if (value.length() > max) {
            return value.substring(0, max);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

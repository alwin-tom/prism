package com.prism.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModerationRiskAssessmentService {

    private final ObjectMapper mapper;

    public ModerationRiskAssessmentService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ModerationAnalysisResult aggregate(List<ModerationAnalysisResult> parts) {
        if (parts == null || parts.isEmpty()) {
            return clean("No textual content was available to moderate.");
        }
        List<ModerationFindingDraft> findings = parts.stream()
                .flatMap(part -> part.findings().stream())
                .toList();
        findings = new ModerationResultValidator().validate(
                summaryNode(findings, parts),
                parts.getLast().modelId(),
                parts.getLast().provider()
        ).findings();

        int critical = (int) findings.stream().filter(item -> "CRITICAL".equals(item.severity())).count();
        int high = (int) findings.stream().filter(item -> "HIGH".equals(item.severity())).count();
        int medium = (int) findings.stream().filter(item -> "MEDIUM".equals(item.severity())).count();
        int low = (int) findings.stream().filter(item -> "LOW".equals(item.severity())).count();
        String risk = "CLEAN";
        for (ModerationFindingDraft finding : findings) {
            risk = ModerationResultValidator.highest(risk, finding.severity());
        }
        String explanation = parts.stream()
                .map(ModerationAnalysisResult::explanation)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left)
                .orElse(findings.isEmpty()
                        ? "No concerning textual content was detected."
                        : "Potential policy issues were flagged for human review.");
        if (!findings.isEmpty() && parts.size() > 1) {
            explanation = "Aggregated moderation across " + parts.size() + " content chunks. " + explanation;
        }
        return new ModerationAnalysisResult(
                risk,
                explanation,
                critical,
                high,
                medium,
                low,
                findings.isEmpty(),
                findings,
                parts.getLast().modelId(),
                parts.getLast().provider()
        );
    }

    public ObjectNode summaryJson(ModerationAnalysisResult result) {
        ObjectNode summary = mapper.createObjectNode();
        summary.put("critical", result.critical());
        summary.put("high", result.high());
        summary.put("medium", result.medium());
        summary.put("low", result.low());
        summary.put("clean", result.clean());
        return summary;
    }

    public Map<String, Object> summaryMap(WebsiteModerationReport report) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("critical", report.summaryJson == null ? 0 : report.summaryJson.path("critical").asInt(0));
        body.put("high", report.summaryJson == null ? 0 : report.summaryJson.path("high").asInt(0));
        body.put("medium", report.summaryJson == null ? 0 : report.summaryJson.path("medium").asInt(0));
        body.put("low", report.summaryJson == null ? 0 : report.summaryJson.path("low").asInt(0));
        body.put("clean", report.summaryJson != null && report.summaryJson.path("clean").asBoolean(false));
        return body;
    }

    public ModerationAnalysisResult clean(String explanation) {
        return new ModerationAnalysisResult("CLEAN", explanation, 0, 0, 0, 0, true, List.of(), null, "openrouter");
    }

    private ObjectNode summaryNode(List<ModerationFindingDraft> findings, List<ModerationAnalysisResult> parts) {
        ObjectNode root = mapper.createObjectNode();
        root.put("overallRisk", "CLEAN");
        root.put("explanation", parts.isEmpty() ? "" : parts.getFirst().explanation());
        ObjectNode summary = root.putObject("summary");
        summary.put("critical", 0);
        summary.put("high", 0);
        summary.put("medium", 0);
        summary.put("low", 0);
        summary.put("clean", findings.isEmpty());
        var array = root.putArray("findings");
        for (ModerationFindingDraft finding : findings) {
            ObjectNode row = array.addObject();
            row.put("category", finding.category());
            row.put("severity", finding.severity());
            row.put("confidence", finding.confidence());
            if (finding.pageId() != null) row.put("pageId", finding.pageId());
            if (finding.sectionId() != null) row.put("sectionId", finding.sectionId());
            if (finding.elementId() != null) row.put("elementId", finding.elementId());
            if (finding.elementType() != null) row.put("elementType", finding.elementType());
            row.put("matchedText", finding.matchedText());
            if (finding.startOffset() != null) row.put("startOffset", finding.startOffset());
            if (finding.endOffset() != null) row.put("endOffset", finding.endOffset());
            if (finding.context() != null) row.put("context", finding.context());
            row.put("reason", finding.reason());
            if (finding.suggestion() != null) row.put("suggestion", finding.suggestion());
        }
        return root;
    }
}

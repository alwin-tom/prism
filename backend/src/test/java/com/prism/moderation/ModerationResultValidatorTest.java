package com.prism.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.creation.OpenRouterException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationResultValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ModerationResultValidator validator = new ModerationResultValidator();
    private final ModerationRiskAssessmentService risk = new ModerationRiskAssessmentService(mapper);

    @Test
    void acceptsCleanResult() {
        ObjectNode body = mapper.createObjectNode();
        body.put("overallRisk", "CLEAN");
        body.put("explanation", "Nothing concerning.");
        ObjectNode summary = body.putObject("summary");
        summary.put("critical", 0).put("high", 0).put("medium", 0).put("low", 0).put("clean", true);
        body.putArray("findings");
        ModerationAnalysisResult result = validator.validate(body, "test-model", "openrouter");
        assertTrue(result.clean());
        assertEquals("CLEAN", result.overallRisk());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void validatesHarassmentFindingAndRaisesOverallRisk() {
        ObjectNode body = mapper.createObjectNode();
        body.put("overallRisk", "LOW");
        body.put("explanation", "Harassment found.");
        body.putObject("summary").put("critical", 0).put("high", 1).put("medium", 0).put("low", 0).put("clean", false);
        ArrayNode findings = body.putArray("findings");
        ObjectNode finding = findings.addObject();
        finding.put("category", "HARASSMENT");
        finding.put("severity", "HIGH");
        finding.put("confidence", 0.96);
        finding.put("pageId", "page-home");
        finding.put("sectionId", "section-hero");
        finding.put("elementId", "text-123");
        finding.put("elementType", "PARAGRAPH");
        finding.put("matchedText", "Exact concerning text");
        finding.put("startOffset", 15);
        finding.put("endOffset", 42);
        finding.put("reason", "Targeted insult");
        finding.put("suggestion", "Please join us for the celebration.");
        ModerationAnalysisResult result = validator.validate(body, "test-model", "openrouter");
        assertEquals("HIGH", result.overallRisk());
        assertEquals(1, result.findings().size());
        assertEquals("HARASSMENT", result.findings().getFirst().category());
        assertEquals("text-123", result.findings().getFirst().elementId());
    }

    @Test
    void rejectsMalformedPayload() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null, "model", "openrouter"));
    }

    @Test
    void aggregatesChunksAndDedupesFindings() {
        ModerationFindingDraft one = new ModerationFindingDraft(
                "PROFANITY", "MEDIUM", java.math.BigDecimal.valueOf(0.8),
                "page-home", "section-1", "el-1", "PARAGRAPH", "damn", 0, 4, null, "Mild profanity", "please"
        );
        ModerationFindingDraft duplicate = new ModerationFindingDraft(
                "PROFANITY", "MEDIUM", java.math.BigDecimal.valueOf(0.7),
                "page-home", "section-1", "el-1", "PARAGRAPH", "damn", 0, 4, null, "Mild profanity again", "please"
        );
        ModerationAnalysisResult left = new ModerationAnalysisResult("MEDIUM", "chunk 1", 0, 0, 1, 0, false, List.of(one), "m1", "openrouter");
        ModerationAnalysisResult right = new ModerationAnalysisResult("MEDIUM", "chunk 2", 0, 0, 1, 0, false, List.of(duplicate), "m1", "openrouter");
        ModerationAnalysisResult aggregated = risk.aggregate(List.of(left, right));
        assertEquals(1, aggregated.findings().size());
        assertEquals("MEDIUM", aggregated.overallRisk());
        assertFalse(aggregated.clean());
    }

    @Test
    void contentModerationServiceUsesProviderChunks() {
        AtomicInteger calls = new AtomicInteger();
        AIContentModerationProvider provider = (chunk, index, total) -> {
            calls.incrementAndGet();
            ObjectNode body = mapper.createObjectNode();
            body.put("overallRisk", "CLEAN");
            body.put("explanation", "ok");
            body.putObject("summary").put("critical", 0).put("high", 0).put("medium", 0).put("low", 0).put("clean", true);
            body.putArray("findings");
            return validator.validate(body, "mock", "openrouter");
        };
        ContentModerationService service = new ContentModerationService(new WebsiteContentExtractionService(), provider, risk);
        ObjectNode document = mapper.createObjectNode();
        document.put("documentId", "page-home");
        document.putObject("page").put("title", "Hello").put("description", "World");
        ObjectNode root = document.putObject("root");
        root.put("id", "root").put("type", "SECTION").put("name", "Root");
        root.putObject("props").put("text", "Friendly welcome");
        root.putObject("styles");
        root.putArray("children");
        ModerationAnalysisResult result = service.analyse(document, null);
        assertTrue(result.clean());
        assertEquals(1, calls.get());
    }

    @Test
    void providerFailuresAreNotTreatedAsClean() {
        AIContentModerationProvider provider = (chunk, index, total) -> {
            throw new OpenRouterException(OpenRouterException.Kind.TIMEOUT, "provider timeout");
        };
        ContentModerationService service = new ContentModerationService(new WebsiteContentExtractionService(), provider, risk);
        ObjectNode document = mapper.createObjectNode();
        document.putObject("page").put("title", "Hello").put("description", "World");
        ObjectNode root = document.putObject("root");
        root.put("id", "root").put("type", "SECTION").put("name", "Root");
        root.putObject("props").put("text", "Friendly welcome");
        root.putObject("styles");
        root.putArray("children");
        assertThrows(OpenRouterException.class, () -> service.analyse(document, null));
    }
}

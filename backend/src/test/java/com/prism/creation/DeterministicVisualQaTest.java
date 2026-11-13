package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicVisualQaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsWithoutFailingWhenBlueprintIsMissing() throws Exception {
        ObjectNode document = baseDocument();
        ObjectNode qa = DeterministicVisualQa.assess(mapper, document, null, "modern-minimal");
        assertTrue(qa.path("checksRun").isArray());
        assertTrue(qa.path("checksRun").toString().contains("vision-unavailable"));
        assertTrue(qa.path("findings").toString().contains("Vision screenshot evaluation did not run"));
    }

    @Test
    void returnsActionableNotesForCriticalFindings() throws Exception {
        ObjectNode document = baseDocument();
        ((ObjectNode) document.path("root").path("children").get(0).path("props")).put("text", "");
        ObjectNode qa = DeterministicVisualQa.assess(mapper, document, mapper.createObjectNode(), "modern-minimal");
        assertTrue(qa.path("findings").size() >= 1);
        assertFalse("pass".equals(qa.path("overallStatus").asText()) && DeterministicVisualQa.actionable(qa).isEmpty());
    }

    private ObjectNode baseDocument() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Sample", "description": ""},
                  "designTokens": {"meta": {"archetypeId": "modern-minimal"}},
                  "root": {
                    "id": "page",
                    "type": "SECTION",
                    "name": "Page",
                    "props": {},
                    "styles": {},
                    "children": [
                      {"id": "title", "type": "HEADING", "name": "Heading", "props": {"text": "Sample", "level": 1}, "styles": {"fontSize": "48px"}, "children": []},
                      {"id": "body", "type": "PARAGRAPH", "name": "Text", "props": {"text": "Welcome."}, "styles": {}, "children": []}
                    ]
                  }
                }
                """);
    }
}

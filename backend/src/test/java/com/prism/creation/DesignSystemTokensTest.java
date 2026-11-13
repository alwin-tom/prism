package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignSystemTokensTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void preservesEmptyMapsForLegacyDocuments() {
        ObjectNode clean = DesignSystemTokens.sanitize(mapper, null);
        assertTrue(clean.path("colors").isObject());
        assertEquals(0, clean.path("colors").size());
        assertTrue(clean.path("fonts").isObject());
        assertTrue(clean.path("spacing").isObject());
    }

    @Test
    void keepsSemanticRolesAndDropsUnsafeValues() throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree("""
                {
                  "colors": {"background":"#0f1720","text":"#f8fafc","primary":"#25B5AE","evil":"url(javascript:1)"},
                  "fonts": {"display":"Fraunces","body":"Manrope","hack":"Arial"},
                  "spacing": {"section":"72px","bad":"url(x)"},
                  "meta": {"archetypeId":"modern-minimal","version":"webaux-creative-v2"}
                }
                """);
        ObjectNode clean = DesignSystemTokens.sanitize(mapper, input);
        assertEquals("#0f1720", clean.path("colors").path("background").asText());
        assertEquals("#25B5AE", clean.path("colors").path("primary").asText());
        assertTrue(clean.path("colors").path("evil").asText("").isBlank());
        assertEquals("Fraunces", clean.path("fonts").path("display").asText());
        assertTrue(clean.path("fonts").path("hack").asText("").isBlank());
        assertEquals("72px", clean.path("spacing").path("section").asText());
        assertEquals("modern-minimal", clean.path("meta").path("archetypeId").asText());
    }

    @Test
    void buildsTokensFromADesignDirection() {
        DesignDirection direction = DesignDirection.resolve(mapper.createObjectNode().put("direction", "elegant-minimal"), "CORPORATE", mapper.createObjectNode());
        ObjectNode tokens = DesignSystemTokens.fromDirection(mapper, direction, "modern-minimal", mapper.createObjectNode().put("motionStrategy", "subtle"));
        assertEquals("modern-minimal", tokens.path("meta").path("archetypeId").asText());
        assertTrue(tokens.path("colors").path("background").asText().startsWith("#"));
        assertEquals("subtle", tokens.path("motion").path("entrance").asText());
    }
}

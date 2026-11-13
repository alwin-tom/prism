package com.prism.typography;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.creation.TypographyApplicator;
import com.prism.editor.DocumentValidator;
import com.prism.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentValidator validator = new DocumentValidator(mapper);

    @Test
    void looksUpWeightsStylesAndAxesFromTheRegistry() {
        assertEquals("Playfair Display", FontRegistry.familyName("playfair-display"));
        assertTrue(FontRegistry.supportsItalic(FontRegistry.font("playfair-display")));
        assertFalse(FontRegistry.supportsItalic(FontRegistry.font("manrope")));
        assertEquals("650", FontRegistry.weight(FontRegistry.font("playfair-display"), "650"));
        assertEquals("700", FontRegistry.weight(FontRegistry.font("libre-baskerville"), "650"));
        assertEquals("400", FontRegistry.weight(FontRegistry.font("bebas-neue"), "700"));
        assertEquals("900", FontRegistry.weight(FontRegistry.font("inter"), "1200"));
        assertEquals("normal", FontRegistry.style(FontRegistry.font("bebas-neue"), "italic"));
        assertEquals("\"wght\" 500", FontRegistry.variations(FontRegistry.font("fraunces"), "\"wght\" 500, \"SOFT\" 40"));
        assertEquals("", FontRegistry.variations(FontRegistry.font("bebas-neue"), "\"wght\" 500"));
        assertTrue(FontRegistry.knownFamily("Fraunces"));
        assertFalse(FontRegistry.knownFamily("Comic Sans"));
    }

    @Test
    void keepsAnOldPageAndRejectsAnUnknownFont() throws Exception {
        ObjectNode document = page();
        ObjectNode clean = validator.sanitize(document);
        assertEquals("Fraunces", clean.path("root").path("children").get(0).path("styles").path("fontFamily").asText());
        assertEquals("Fraunces", clean.path("typography").path("roles").path("h1").path("fontFamily").asText());
        assertEquals("Newsreader", clean.path("typography").path("roles").path("body").path("fontFamily").asText());
        ((ObjectNode) document.path("root").path("children").get(0).path("styles")).put("fontFamily", "Comic Sans");
        assertThrows(ApiException.class, () -> validator.sanitize(document));
    }

    @Test
    void acceptsARegistryFontAndSnapsAnUnsupportedWeight() throws Exception {
        ObjectNode document = page();
        ObjectNode styles = (ObjectNode) document.path("root").path("children").get(0).path("styles");
        styles.put("fontId", "libre-baskerville");
        styles.put("fontFamily", "Libre Baskerville");
        styles.put("fontWeight", "650");
        styles.put("fontStyle", "italic");
        ObjectNode clean = validator.sanitize(document);
        ObjectNode heading = (ObjectNode) clean.path("root").path("children").get(0).path("styles");
        assertEquals("Libre Baskerville", heading.path("fontFamily").asText());
        assertEquals("700", heading.path("fontWeight").asText());
        assertEquals("italic", heading.path("fontStyle").asText());
    }

    @Test
    void appliesADirectionPairingWithoutChangingTheWords() throws Exception {
        ObjectNode document = page();
        TypographyApplicator.apply(document, null, "luxury-romantic");
        assertEquals("Playfair Display", document.path("root").path("children").get(0).path("styles").path("fontFamily").asText());
        assertEquals("Manrope", document.path("root").path("children").get(1).path("styles").path("fontFamily").asText());
        assertEquals("Hello", document.path("root").path("children").get(0).path("props").path("text").asText());
        assertEquals("playfair-display", document.path("typography").path("roles").path("display").path("fontId").asText());
        assertEquals("1.7", document.path("typography").path("roles").path("body").path("lineHeight").asText());
    }

    @Test
    void repairsAnUnknownFontIdAndKeepsAMobileSize() throws Exception {
        ObjectNode document = page();
        ObjectNode patch = (ObjectNode) mapper.readTree("""
                {"summary":"Smaller on a phone","presetId":"","nodes":[
                  {"id":"title","fontId":"https://evil.example/font.css","fontWeight":"","fontStyle":"","letterSpacing":"-0.04em","lineHeight":"","fontSize":"","tabletFontSize":"","mobileFontSize":"28px"},
                  {"id":"copy","fontId":"great-vibes","fontWeight":"","fontStyle":"","letterSpacing":"","lineHeight":"","fontSize":"","tabletFontSize":"","mobileFontSize":""}
                ]}
                """);
        List<String> warnings = new ArrayList<>();
        TypographyPatch.apply(mapper, document, patch, warnings);
        assertEquals("Fraunces", document.path("root").path("children").get(0).path("styles").path("fontFamily").asText());
        assertEquals("-0.04em", document.path("root").path("children").get(0).path("styles").path("letterSpacing").asText());
        assertEquals("28px", document.path("root").path("children").get(0).path("responsiveStyles").path("mobile").path("fontSize").asText());
        assertEquals("64px", document.path("root").path("children").get(0).path("styles").path("fontSize").asText());
        assertEquals("Newsreader", document.path("root").path("children").get(1).path("styles").path("fontFamily").asText());
        assertEquals("Hello", document.path("root").path("children").get(0).path("props").path("text").asText());
        assertFalse(warnings.isEmpty());
    }

    private ObjectNode page() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {
                  "schemaVersion": 1,
                  "documentId": "11111111-1111-1111-1111-111111111111",
                  "page": {"title": "Anna", "description": ""},
                  "root": {
                    "id": "page", "type": "SECTION", "name": "Page", "props": {}, "styles": {"fontFamily": "Fraunces"},
                    "children": [
                      {"id": "title", "type": "HEADING", "name": "Heading", "props": {"text": "Hello", "level": 1}, "styles": {"fontFamily": "Fraunces", "fontSize": "64px"}, "children": []},
                      {"id": "copy", "type": "PARAGRAPH", "name": "Paragraph", "props": {"text": "A paragraph for the guests."}, "styles": {"fontFamily": "Newsreader", "fontSize": "18px"}, "children": []}
                    ]
                  }
                }
                """);
    }
}

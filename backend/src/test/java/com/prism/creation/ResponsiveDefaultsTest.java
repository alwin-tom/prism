package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveDefaultsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesSparseOverridesAndLeavesExistingOnes() throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {
                  "root": {
                    "id": "page", "type": "SECTION", "styles": {"padding": "80px 40px", "flexDirection": "column", "gap": "32px"},
                    "responsiveStyles": {"mobile": {"padding": "20px"}},
                    "children": [
                      {"id": "title", "type": "HEADING", "styles": {"fontSize": "64px"}, "children": []},
                      {"id": "row", "type": "FLEX", "styles": {"flexDirection": "row", "gap": "28px"}, "children": []},
                      {"id": "grid", "type": "GRID", "styles": {"gridTemplateColumns": "1fr 1fr 1fr"}, "children": []},
                      {"id": "go", "type": "BUTTON", "styles": {"width": "180px"}, "children": []},
                      {"id": "photos", "type": "CAROUSEL", "props": {"perView": 3}, "styles": {"gap": "24px"}, "children": []}
                    ]
                  }
                }
                """);
        ResponsiveDefaults.apply(document);
        ObjectNode title = (ObjectNode) document.path("root").path("children").get(0);
        assertEquals("64px", title.path("styles").path("fontSize").asText());
        assertEquals("50px", title.path("responsiveStyles").path("tablet").path("fontSize").asText());
        assertEquals("36px", title.path("responsiveStyles").path("mobile").path("fontSize").asText());
        assertEquals("20px", document.path("root").path("responsiveStyles").path("mobile").path("padding").asText());
        assertEquals("56px 28px", document.path("root").path("responsiveStyles").path("tablet").path("padding").asText());
        assertEquals("column", document.path("root").path("children").get(1).path("responsiveStyles").path("mobile").path("flexDirection").asText());
        assertEquals("", document.path("root").path("children").get(1).path("responsiveStyles").path("tablet").path("flexDirection").asText(""));
        assertEquals("1fr", document.path("root").path("children").get(2).path("responsiveStyles").path("mobile").path("gridTemplateColumns").asText());
        assertEquals("1fr 1fr", document.path("root").path("children").get(2).path("responsiveStyles").path("tablet").path("gridTemplateColumns").asText());
        assertEquals("100%", document.path("root").path("children").get(3).path("responsiveStyles").path("mobile").path("width").asText());
        assertEquals(3, document.path("root").path("children").get(4).path("props").path("perView").asInt());
        assertEquals(2, document.path("root").path("children").get(4).path("responsiveProps").path("tablet").path("perView").asInt());
        assertEquals(1, document.path("root").path("children").get(4).path("responsiveProps").path("mobile").path("perView").asInt());
        assertTrue(document.path("root").path("children").get(0).path("responsiveStyles").path("mobile").size() == 1);
    }
}

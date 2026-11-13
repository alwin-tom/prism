package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignDirectionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void givesEachCategoryADifferentIdentity() {
        List<String> categories = List.of("WEDDING", "BIRTHDAY", "BABY", "CONFERENCE", "BUSINESS");
        Set<String> heroes = new HashSet<>();
        Set<String> fonts = new HashSet<>();
        for (String category : categories) {
            DesignDirection direction = DesignDirection.resolve(mapper.createObjectNode(), category, mapper.createObjectNode());
            heroes.add(direction.heroBackground());
            fonts.add(direction.displayFont() + "/" + direction.bodyFont());
        }
        assertEquals(5, heroes.size());
        assertTrue(fonts.size() >= 3);
    }

    @Test
    void paintsTheChosenPaletteOntoAPlainPage() throws Exception {
        DesignDirection direction = DesignDirection.resolve(mapper.createObjectNode().put("direction", "luxury-romantic"), "WEDDING", mapper.createObjectNode());
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {"schemaVersion":1,"documentId":"11111111-1111-1111-1111-111111111111","page":{"title":"Ada","description":""},"root":{"id":"page","type":"SECTION","name":"Page","props":{"text":""},"styles":{"background":"#ffffff","color":"#111111","fontFamily":"IBM Plex Sans"},"children":[{"id":"title","type":"HEADING","name":"Heading","props":{"text":"Ada and Noor","level":1},"styles":{"fontFamily":"IBM Plex Sans","color":"#111111"},"children":[]},{"id":"story","type":"SECTION","name":"Story","props":{"text":""},"styles":{"background":"#ffffff"},"children":[{"id":"copy","type":"PARAGRAPH","name":"Paragraph","props":{"text":"Ada and Noor welcome their guests to a celebration in Aluva."},"styles":{},"children":[]}]}]}}
                """);
        ObjectNode painted = DesignApplicator.apply(document, direction);
        assertEquals("Ada and Noor", painted.path("root").path("children").get(0).path("props").path("text").asText());
        assertTrue(painted.path("root").path("styles").path("background").asText().contains("gradient"));
        assertEquals("Fraunces", painted.path("root").path("children").get(0).path("styles").path("fontFamily").asText());
        assertNotEquals("#ffffff", painted.path("root").path("children").get(1).path("styles").path("background").asText());
        DesignQuality.Result quality = DesignQuality.assess(painted, direction);
        assertTrue(quality.overall() >= 70, "score " + quality.overall() + " " + quality.issues());
    }

    @Test
    void keepsASectionOnOneAlignment() throws Exception {
        DesignDirection direction = DesignDirection.resolve(mapper.createObjectNode().put("direction", "luxury-romantic"), "WEDDING", mapper.createObjectNode());
        ObjectNode document = (ObjectNode) mapper.readTree("""
                {"schemaVersion":1,"root":{"id":"page","type":"SECTION","props":{},"styles":{},"children":[
                  {"id":"row","type":"FLEX","props":{},"styles":{"flexDirection":"row"},"children":[
                    {"id":"photo","type":"IMAGE","props":{},"styles":{"position":"relative","left":"35%","top":"-200px","width":"40%","marginLeft":"0","textAlign":"left"},"children":[]}
                  ]},
                  {"id":"notes","type":"SECTION","props":{},"styles":{"textAlign":"left"},"children":[
                    {"id":"title","type":"HEADING","props":{"text":"Notes","level":2},"styles":{"textAlign":"left"},"children":[]},
                    {"id":"show","type":"CAROUSEL","props":{},"styles":{"width":"450px","textAlign":"center","position":"relative","left":"27%"},"children":[]}
                  ]}
                ]}}
                """);
        ObjectNode painted = DesignApplicator.apply(document, direction);
        ObjectNode photo = (ObjectNode) painted.path("root").path("children").get(0).path("children").get(0).path("styles");
        assertEquals("", photo.path("position").asText(""));
        assertEquals("", photo.path("left").asText(""));
        assertEquals("", photo.path("top").asText(""));
        assertEquals("flex", painted.path("root").path("children").get(0).path("styles").path("display").asText());
        ObjectNode notes = (ObjectNode) painted.path("root").path("children").get(1).path("styles");
        assertEquals("center", notes.path("textAlign").asText());
        assertEquals("center", painted.path("root").path("children").get(1).path("children").get(0).path("styles").path("textAlign").asText());
        ObjectNode show = (ObjectNode) painted.path("root").path("children").get(1).path("children").get(1).path("styles");
        assertEquals("auto", show.path("marginLeft").asText());
        assertEquals("", show.path("position").asText(""));
    }
}

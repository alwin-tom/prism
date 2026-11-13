package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchetypeCatalogTest {

    private final ArchetypeCatalog catalog = new ArchetypeCatalog(new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsTenCrossCategoryArchetypes() {
        assertEquals("1.0", catalog.catalogueVersion());
        assertEquals(10, catalog.all().size());
        assertFalse(catalog.forCategory("WEDDING").isEmpty());
        assertTrue(catalog.byId("luxury-editorial").isPresent());
    }

    @Test
    void userPreferenceTakesPrecedenceWhenCompatible() {
        assertEquals("bold-typographic", catalog.resolvePreference("bold-typographic", "PORTFOLIO", mapper.createObjectNode()));
    }

    @Test
    void ignoresIncompatiblePreferenceAndPicksCategoryDefault() {
        String chosen = catalog.resolvePreference("warm-storybook", "CORPORATE", mapper.createObjectNode());
        assertTrue(catalog.byId(chosen).isPresent());
        assertTrue(catalog.forCategory("CORPORATE").stream().anyMatch(node -> chosen.equals(node.path("id").asText())));
        assertFalse("warm-storybook".equals(chosen));
    }

    @Test
    void mapsArchetypeOntoExistingDesignDirectionIds() {
        assertEquals("elegant-minimal", catalog.directionId("modern-minimal"));
        assertEquals("cinematic-dark", catalog.directionId("cinematic-luxury"));
    }

    @Test
    void aiChoiceFallsBackFromAnswersStyle() {
        ObjectNode answers = mapper.createObjectNode().put("style", "playful");
        assertEquals("playful-contemporary", catalog.resolvePreference("ai", "BIRTHDAY", answers));
    }
}

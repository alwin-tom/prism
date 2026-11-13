package com.prism.designlibrary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignReferenceEmbeddingServiceTest {

    @Test
    void formatsPgVectorLiterals() {
        assertEquals("[1.0,0.5,-0.25]", DesignReferenceEmbeddingService.toVectorLiteral(new float[]{1f, 0.5f, -0.25f}));
    }

    @Test
    void buildsEmbedTextFromReferenceFields() {
        DesignReference row = new DesignReference();
        row.name = "Quiet Luxury";
        row.description = "Minimal wedding";
        row.eventCategory = "WEDDING";
        row.archetypeId = "modern-minimal";
        row.mood = new String[]{"calm"};
        row.styleTags = new String[]{"quiet"};
        row.keywords = new String[]{"wedding"};
        String text = DesignReferenceEmbeddingService.embedText(row);
        assertTrue(text.contains("Quiet Luxury"));
        assertTrue(text.contains("modern-minimal"));
    }
}

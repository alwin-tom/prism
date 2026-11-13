package com.prism.designlibrary;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignLibrarySeedCatalogTest {

    @Test
    void seedsFortyDistinctOriginalConcepts() {
        assertEquals(40, DesignLibrarySeedCatalog.originals().size());
        Set<String> keys = new HashSet<>();
        for (DesignLibrarySeedCatalog.Seed seed : DesignLibrarySeedCatalog.originals()) {
            assertTrue(keys.add(seed.seedKey()), "duplicate seed key " + seed.seedKey());
            assertEquals("ORIGINAL", seed.sourceType());
            assertEquals("APPROVED", seed.status());
            assertTrue(seed.qualityScore().doubleValue() >= 7.0);
        }
    }

    @Test
    void inspirationLinksAreUrlOnly() {
        assertTrue(DesignLibrarySeedCatalog.inspirationLinks().size() >= 5);
        for (DesignLibrarySeedCatalog.Seed seed : DesignLibrarySeedCatalog.inspirationLinks()) {
            assertEquals("INSPIRATION_LINK", seed.sourceType());
            assertTrue(seed.inspirationUrl() != null && seed.inspirationUrl().startsWith("https://"));
        }
    }
}

package com.prism.imagegen;

import com.prism.shared.config.PrismProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageModelSelectorTest {

    private PrismProperties properties;
    private ScriptedDiscovery discovery;
    private ImageModelSelector selector;

    @BeforeEach
    void setUp() {
        properties = new PrismProperties();
        properties.getImage().setEnabled(true);
        properties.getImage().setFreeFirst(true);
        properties.getImage().setPaidFallbackEnabled(false);
        properties.getImage().setMaxCostPerImageUsd(new BigDecimal("0.05"));
        properties.getImage().setMaxCostPerWebsiteUsd(new BigDecimal("0.15"));
        discovery = new ScriptedDiscovery();
        selector = new ImageModelSelector(discovery, properties);
    }

    @Test
    void prefersFreeWhenAvailable() {
        discovery.cards = List.of(
                card("paid/model", false, new BigDecimal("0.04"), true),
                card("free/model:free", true, BigDecimal.ZERO, true)
        );
        ImageModelSelector.Selection selection = selector.select("16:9", BigDecimal.ZERO);
        assertFalse(selection.skip());
        assertEquals("free/model:free", selection.model().id());
    }

    @Test
    void blocksPaidWhenFallbackDisabled() {
        discovery.cards = List.of(card("paid/model", false, new BigDecimal("0.04"), true));
        ImageModelSelector.Selection selection = selector.select("16:9", BigDecimal.ZERO);
        assertTrue(selection.skip());
    }

    @Test
    void paidFallbackRespectsBudget() {
        properties.getImage().setPaidFallbackEnabled(true);
        discovery.cards = List.of(card("paid/model", false, new BigDecimal("0.04"), true));
        ImageModelSelector.Selection ok = selector.select("16:9", BigDecimal.ZERO);
        assertFalse(ok.skip());
        ImageModelSelector.Selection blocked = selector.select("16:9", new BigDecimal("0.14"));
        assertTrue(blocked.skip());
    }

    @Test
    void unknownPricingDoesNotBypassPaidBudget() {
        properties.getImage().setPaidFallbackEnabled(true);
        discovery.cards = List.of(card("mystery/model", false, null, false));
        ImageModelSelector.Selection selection = selector.select("16:9", BigDecimal.ZERO);
        assertTrue(selection.skip());
    }

    private static ImageModelCard card(String id, boolean free, BigDecimal price, boolean known) {
        return new ImageModelCard(id, id, "", Set.of("16:9"), Set.of(), Set.of(), price, known, free);
    }

    private static final class ScriptedDiscovery extends ImageModelDiscoveryService {
        private List<ImageModelCard> cards = List.of();

        private ScriptedDiscovery() {
            super(new PrismProperties(), new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public List<ImageModelCard> list(boolean forceRefresh) {
            return cards;
        }

        @Override
        public List<ImageModelCard> list() {
            return cards;
        }
    }
}

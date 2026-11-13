package com.prism.creation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterModelsTest {

    @Test
    void classifiesFreeRouterFreeModelsAndFloorRouting() {
        assertEquals(OpenRouterModels.Tier.FREE_ROUTER, OpenRouterModels.tier("openrouter/free"));
        assertEquals(OpenRouterModels.Tier.FREE, OpenRouterModels.tier("meta/llama:free"));
        assertEquals(OpenRouterModels.Tier.FLOOR, OpenRouterModels.tier("openai/gpt-4o:floor"));
        assertEquals(OpenRouterModels.Tier.PAID, OpenRouterModels.tier("openai/gpt-4o"));
        assertTrue(OpenRouterModels.isFree("openrouter/free"));
        assertTrue(OpenRouterModels.isPaid("openai/gpt-4o:floor"));
    }

    @Test
    void keepsAFreeChainAndDropsPaidFallbacks() {
        OpenRouterModels.Facts unsupported = new OpenRouterModels.Facts() {
            @Override
            public Boolean supportsStructured(String model) {
                return model.endsWith(":free") ? Boolean.FALSE : null;
            }

            @Override
            public Boolean zeroPrice(String model) {
                return null;
            }
        };
        assertEquals(List.of("openrouter/free"), OpenRouterModels.chain("openrouter/free", List.of("meta/llama:free", "openai/gpt-4o"), false, true, 2, unsupported));
        assertEquals(List.of("openai/gpt-4o:floor"), OpenRouterModels.chain("openai/gpt-4o:floor", List.of("openai/gpt-4o-mini"), false, false, 2, null));
        assertEquals(List.of("openai/gpt-4o:floor", "openai/gpt-4o-mini:floor"), OpenRouterModels.chain("openai/gpt-4o:floor", List.of("openai/gpt-4o-mini:floor"), true, false, 2, null));
    }

    @Test
    void rejectsAPaidResultForAFreeRequest() {
        assertFalse(OpenRouterModels.acceptsFreeResult("openrouter/free", "openai/gpt-4o", new BigDecimal("0.01"), null));
        assertFalse(OpenRouterModels.acceptsFreeResult("openrouter/free", "openai/gpt-4o", BigDecimal.ZERO, null));
        assertTrue(OpenRouterModels.acceptsFreeResult("openrouter/free", "meta/llama:free", BigDecimal.ZERO, null));
        assertTrue(OpenRouterModels.acceptsFreeResult("openai/gpt-4o", "openai/gpt-4o", new BigDecimal("0.02"), null));
        OpenRouterModels.Facts freePrice = new OpenRouterModels.Facts() {
            @Override
            public Boolean supportsStructured(String model) {
                return null;
            }

            @Override
            public Boolean zeroPrice(String model) {
                return "included/free".equals(model);
            }
        };
        assertTrue(OpenRouterModels.acceptsFreeResult("openrouter/free", "included/free", null, freePrice));
    }
}

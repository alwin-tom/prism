package com.prism.creation;

import com.prism.shared.config.PrismProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteUiModelSelectorTest {

    @Test
    void missingFlagDefaultsToFreeMode() {
        PrismProperties properties = properties(null);
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        assertTrue(selector.useFreeUiModels());
        assertEquals(WebsiteUiModelSelector.Strategy.FREE, selector.strategy());
    }

    @Test
    void trueSelectsFreeStrategy() {
        PrismProperties properties = properties("true");
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        WebsiteUiModelSelector.Selection selection = selector.select(
                WebsiteUiModelSelector.Task.GENERATION,
                openrouter("free-plan", "free-gen", "free-repair")
        );
        assertEquals(WebsiteUiModelSelector.Strategy.FREE, selection.strategy());
        assertEquals("free-gen", selection.model());
        assertFalse(selection.allowPaidFallback());
    }

    @Test
    void invalidFlagDefaultsToFreeMode() {
        PrismProperties properties = properties("maybe");
        assertTrue(properties.getUiModels().isUseFreeUiModels());
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        assertEquals(WebsiteUiModelSelector.Strategy.FREE, selector.strategy());
    }

    @Test
    void falseSelectsPremiumStrategy() {
        PrismProperties properties = properties("false");
        properties.getUiModels().setPremiumPrimaryModel("anthropic/claude-opus-5.5");
        properties.getUiModels().setPremiumSecondaryModel("openai/gpt-6-astra");
        properties.getUiModels().setPremiumFallbackModel("google/gemini-4-carbon");
        properties.getUiModels().setPremiumRefinementModel("moonshotai/kimi-k3.1-pro");
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);

        WebsiteUiModelSelector.Selection planning = selector.select(WebsiteUiModelSelector.Task.PLANNING, openrouter("ignored", "ignored", "ignored"));
        assertEquals(WebsiteUiModelSelector.Strategy.PREMIUM, planning.strategy());
        assertEquals("anthropic/claude-opus-5.5", planning.model());
        assertTrue(planning.allowPaidFallback());
        assertTrue(planning.fallbacks().contains("openai/gpt-6-astra"));
        assertFalse(planning.fallbacks().contains("ignored"));

        WebsiteUiModelSelector.Selection generation = selector.select(WebsiteUiModelSelector.Task.GENERATION, openrouter("ignored", "ignored", "ignored"));
        assertEquals("openai/gpt-6-astra", generation.model());
        assertTrue(generation.fallbacks().contains("anthropic/claude-opus-5.5"));

        WebsiteUiModelSelector.Selection refine = selector.select(WebsiteUiModelSelector.Task.REFINEMENT, openrouter("ignored", "ignored", "ignored"));
        assertEquals("moonshotai/kimi-k3.1-pro", refine.model());
    }

    @Test
    void premiumConfigurationIsReadFromProperties() {
        PrismProperties properties = properties("false");
        properties.getUiModels().setPremiumPrimaryModel("anthropic/claude-opus-5.5");
        properties.getUiModels().setPremiumSecondaryModel("openai/gpt-6-astra");
        properties.getUiModels().setPremiumFallbackModel("google/gemini-4-carbon");
        properties.getUiModels().setPremiumRefinementModel("moonshotai/kimi-k3.1-pro");
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        WebsiteUiModelSelector.Selection repair = selector.select(WebsiteUiModelSelector.Task.REPAIR, openrouter("a", "b", "c"));
        assertEquals("moonshotai/kimi-k3.1-pro", repair.model());
        assertEquals(WebsiteUiModelSelector.Strategy.PREMIUM, repair.strategy());
    }

    @Test
    void premiumFailureChainStaysWithinPremiumModels() {
        PrismProperties properties = properties("0");
        properties.getUiModels().setPremiumPrimaryModel("anthropic/claude-opus-5.5");
        properties.getUiModels().setPremiumSecondaryModel("");
        properties.getUiModels().setPremiumFallbackModel("google/gemini-4-carbon");
        properties.getUiModels().setPremiumRefinementModel("");
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        WebsiteUiModelSelector.Selection selection = selector.select(
                WebsiteUiModelSelector.Task.GENERATION,
                openrouter("openrouter/free", "openrouter/free", "openrouter/free")
        );
        assertEquals("anthropic/claude-opus-5.5", selection.model());
        assertEquals(java.util.List.of("google/gemini-4-carbon"), selection.fallbacks());
        assertFalse(selection.fallbacks().stream().anyMatch(OpenRouterModels::isFree));
    }

    @Test
    void premiumModeWithNoModelsConfiguredFailsClearly() {
        PrismProperties properties = properties("false");
        properties.getUiModels().setPremiumPrimaryModel("");
        properties.getUiModels().setPremiumSecondaryModel("");
        properties.getUiModels().setPremiumFallbackModel("");
        properties.getUiModels().setPremiumRefinementModel("");
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        OpenRouterException exception = assertThrows(
                OpenRouterException.class,
                () -> selector.select(WebsiteUiModelSelector.Task.PLANNING, openrouter("free", "free", "free"))
        );
        assertTrue(exception.getMessage().contains("Premium UI models"));
    }

    @Test
    void freeModeKeepsExistingModelSettings() {
        PrismProperties properties = properties("yes");
        WebsiteUiModelSelector selector = new WebsiteUiModelSelector(properties);
        PrismProperties.OpenRouter settings = openrouter("plan-a", "gen-b", "repair-c");
        settings.setPlanningFallbacks("plan-fallback");
        settings.setGenerationFallbacks("gen-fallback");
        settings.setRepairFallbacks("repair-fallback");
        settings.setAllowPaidFallback(false);

        assertEquals("plan-a", selector.select(WebsiteUiModelSelector.Task.PLANNING, settings).model());
        assertEquals(java.util.List.of("plan-fallback"), selector.select(WebsiteUiModelSelector.Task.PLANNING, settings).fallbacks());
        assertEquals("gen-b", selector.select(WebsiteUiModelSelector.Task.GENERATION, settings).model());
        assertEquals("repair-c", selector.select(WebsiteUiModelSelector.Task.REPAIR, settings).model());
        assertEquals(WebsiteUiModelSelector.Strategy.FREE, selector.select(WebsiteUiModelSelector.Task.REPAIR, settings).strategy());
    }

    private static PrismProperties properties(String flag) {
        PrismProperties properties = new PrismProperties();
        properties.getUiModels().setUseFreeUiModels(flag);
        return properties;
    }

    private static PrismProperties.OpenRouter openrouter(String planning, String generation, String repair) {
        PrismProperties.OpenRouter settings = new PrismProperties.OpenRouter();
        settings.setPlanningModel(planning);
        settings.setGenerationModel(generation);
        settings.setRepairModel(repair);
        settings.setAllowPaidFallback(false);
        return settings;
    }
}

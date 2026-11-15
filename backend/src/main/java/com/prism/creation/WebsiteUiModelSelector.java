package com.prism.creation;

import com.prism.shared.config.PrismProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Resolves OpenRouter models for website-design generation only.
 * Free mode preserves the existing planning/generation/repair settings.
 * Premium mode uses WEBAUX_PREMIUM_UI_* models and never falls back to free models.
 */
@Service
public class WebsiteUiModelSelector {

    private static final Logger log = LoggerFactory.getLogger(WebsiteUiModelSelector.class);

    public enum Strategy {
        FREE, PREMIUM
    }

    public enum Task {
        PLANNING, GENERATION, REPAIR, REFINEMENT
    }

    public record Selection(
            Strategy strategy,
            Task task,
            String model,
            List<String> fallbacks,
            boolean allowPaidFallback
    ) {
        public Selection {
            fallbacks = List.copyOf(fallbacks == null ? List.of() : fallbacks);
        }
    }

    private final PrismProperties properties;

    public WebsiteUiModelSelector(PrismProperties properties) {
        this.properties = properties;
    }

    public Strategy strategy() {
        return properties.getUiModels().isUseFreeUiModels() ? Strategy.FREE : Strategy.PREMIUM;
    }

    public boolean useFreeUiModels() {
        return strategy() == Strategy.FREE;
    }

    public Selection select(Task task, PrismProperties.OpenRouter settings) {
        if (useFreeUiModels()) {
            return free(task, settings);
        }
        return premium(task);
    }

    private Selection free(Task task, PrismProperties.OpenRouter settings) {
        PrismProperties.OpenRouter openrouter = settings == null ? properties.getOpenrouter() : settings;
        Selection selection = switch (task) {
            case PLANNING -> new Selection(
                    Strategy.FREE,
                    task,
                    firstNonBlank(openrouter.getPlanningModel(), openrouter.getGenerationModel()),
                    OpenRouterModels.split(openrouter.getPlanningFallbacks()),
                    openrouter.isAllowPaidFallback()
            );
            case GENERATION -> new Selection(
                    Strategy.FREE,
                    task,
                    firstNonBlank(openrouter.getGenerationModel(), openrouter.getPlanningModel()),
                    OpenRouterModels.split(openrouter.getGenerationFallbacks()),
                    openrouter.isAllowPaidFallback()
            );
            case REPAIR, REFINEMENT -> new Selection(
                    Strategy.FREE,
                    task,
                    firstNonBlank(openrouter.getRepairModel(), openrouter.getGenerationModel()),
                    OpenRouterModels.split(openrouter.getRepairFallbacks()),
                    openrouter.isAllowPaidFallback()
            );
        };
        log.info(
                "Website UI model strategy={} task={} model={} fallbacks={}",
                selection.strategy(),
                selection.task(),
                selection.model(),
                selection.fallbacks()
        );
        return selection;
    }

    private Selection premium(Task task) {
        PrismProperties.UiModels ui = properties.getUiModels();
        String primary = trim(ui.getPremiumPrimaryModel());
        String secondary = trim(ui.getPremiumSecondaryModel());
        String fallback = trim(ui.getPremiumFallbackModel());
        String refinement = trim(ui.getPremiumRefinementModel());

        String model;
        List<String> chain = new ArrayList<>();
        switch (task) {
            case PLANNING -> {
                model = firstNonBlank(primary, secondary, fallback, refinement);
                chain.addAll(listNonBlank(secondary, fallback, refinement));
            }
            case GENERATION -> {
                // Prefer structured-output specialist, then primary design model.
                model = firstNonBlank(secondary, primary, fallback, refinement);
                chain.addAll(listNonBlank(primary, fallback, refinement));
            }
            case REPAIR, REFINEMENT -> {
                model = firstNonBlank(refinement, fallback, secondary, primary);
                chain.addAll(listNonBlank(fallback, secondary, primary));
            }
            default -> {
                model = firstNonBlank(primary, secondary, fallback, refinement);
                chain.addAll(listNonBlank(secondary, fallback, refinement));
            }
        }
        if (model == null || model.isBlank()) {
            throw new OpenRouterException(
                    OpenRouterException.Kind.AUTH,
                    "Premium UI models are enabled but no WEBAUX_PREMIUM_UI_* model ids are configured."
            );
        }
        List<String> fallbacks = distinctExcept(chain, model);
        log.info(
                "Website UI model strategy={} task={} model={} fallbacks={}",
                Strategy.PREMIUM,
                task,
                model,
                fallbacks
        );
        return new Selection(Strategy.PREMIUM, task, model, fallbacks, true);
    }

    private static List<String> distinctExcept(List<String> values, String primary) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        String primaryKey = primary == null ? "" : primary.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.toLowerCase(Locale.ROOT).equals(primaryKey)) {
                continue;
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static List<String> listNonBlank(String... values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isBlank()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

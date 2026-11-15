package com.prism.imagegen;

import com.prism.creation.OpenRouterModels;
import com.prism.shared.config.PrismProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ImageModelSelector {

    private final ImageModelDiscoveryService discovery;
    private final PrismProperties properties;

    public ImageModelSelector(ImageModelDiscoveryService discovery, PrismProperties properties) {
        this.discovery = discovery;
        this.properties = properties;
    }

    public Selection select(String aspectRatio, BigDecimal websiteSpent) {
        PrismProperties.Image config = properties.getImage();
        List<ImageModelCard> models = discovery.list();
        if (models.isEmpty()) {
            return Selection.skip("No compatible image models are available.");
        }
        Set<String> preferred = preferredIds(config.getPreferredModels());
        List<ImageModelCard> compatible = new ArrayList<>();
        for (ImageModelCard card : models) {
            if (card.supportsAspect(aspectRatio)) {
                compatible.add(card);
            }
        }
        if (compatible.isEmpty()) {
            compatible = new ArrayList<>(models);
        }
        compatible.sort(Comparator
                .comparing((ImageModelCard c) -> !preferred.contains(c.id()))
                .thenComparing(c -> !c.free())
                .thenComparing(c -> c.pricePerImageUsd() == null ? BigDecimal.valueOf(999) : c.pricePerImageUsd())
                .thenComparing(ImageModelCard::id));

        BigDecimal remainingWebsite = remaining(config.getMaxCostPerWebsiteUsd(), websiteSpent);
        BigDecimal maxPerImage = config.getMaxCostPerImageUsd();

        if (config.isFreeFirst()) {
            for (ImageModelCard card : compatible) {
                if (card.free() && card.priceKnown() && withinBudget(card.pricePerImageUsd(), maxPerImage, remainingWebsite)) {
                    return Selection.choose(card, card.pricePerImageUsd());
                }
            }
            for (ImageModelCard card : compatible) {
                if (card.free() && !card.priceKnown()) {
                    return Selection.choose(card, BigDecimal.ZERO);
                }
            }
        }

        if (!config.isPaidFallbackEnabled()) {
            return Selection.skip("No free image model is available and paid fallback is disabled.");
        }

        for (ImageModelCard card : compatible) {
            if (card.free()) {
                continue;
            }
            if (!card.priceKnown() || card.pricePerImageUsd() == null) {
                continue;
            }
            if (withinBudget(card.pricePerImageUsd(), maxPerImage, remainingWebsite)) {
                return Selection.choose(card, card.pricePerImageUsd());
            }
        }
        return Selection.skip("No eligible image model fits the configured budget.");
    }

    private static boolean withinBudget(BigDecimal price, BigDecimal maxPerImage, BigDecimal remainingWebsite) {
        if (price == null) {
            return false;
        }
        if (maxPerImage != null && maxPerImage.compareTo(BigDecimal.ZERO) > 0 && price.compareTo(maxPerImage) > 0) {
            return false;
        }
        if (remainingWebsite != null && remainingWebsite.compareTo(BigDecimal.ZERO) >= 0 && price.compareTo(remainingWebsite) > 0) {
            return false;
        }
        return true;
    }

    private static BigDecimal remaining(BigDecimal maxWebsite, BigDecimal spent) {
        if (maxWebsite == null || maxWebsite.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("999999");
        }
        BigDecimal used = spent == null ? BigDecimal.ZERO : spent;
        return maxWebsite.subtract(used).max(BigDecimal.ZERO);
    }

    private static Set<String> preferredIds(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        for (String part : OpenRouterModels.split(raw)) {
            ids.add(part.toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    public record Selection(boolean skip, String reason, ImageModelCard model, BigDecimal estimatedCost) {
        public static Selection skip(String reason) {
            return new Selection(true, reason, null, null);
        }

        public static Selection choose(ImageModelCard model, BigDecimal estimatedCost) {
            return new Selection(false, null, model, estimatedCost == null ? BigDecimal.ZERO : estimatedCost);
        }
    }
}

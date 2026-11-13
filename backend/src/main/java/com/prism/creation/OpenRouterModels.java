package com.prism.creation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class OpenRouterModels {

    public static final String FREE_ROUTER = "openrouter/free";
    public static final int MAX_FALLBACKS = 2;
    public static final int MAX_RETRIES = 2;
    public static final int MAX_REQUESTS = 12;
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_./:@+-]{0,159}$");

    public enum Tier {
        FREE_ROUTER, FREE, FLOOR, PAID
    }

    public interface Facts {
        /** Null when the model catalog does not know this id. */
        Boolean supportsStructured(String model);

        /** Null when the price is unknown. True when prompt and completion prices are zero. */
        Boolean zeroPrice(String model);
    }

    private OpenRouterModels() {
    }

    public static Tier tier(String model) {
        String id = normalize(model);
        if (FREE_ROUTER.equals(id)) {
            return Tier.FREE_ROUTER;
        }
        if (id.endsWith(":free")) {
            return Tier.FREE;
        }
        if (id.endsWith(":floor")) {
            return Tier.FLOOR;
        }
        return Tier.PAID;
    }

    public static boolean isFree(String model) {
        Tier tier = tier(model);
        return tier == Tier.FREE_ROUTER || tier == Tier.FREE;
    }

    public static boolean isPaid(String model) {
        return !isFree(model);
    }

    public static boolean validId(String model) {
        return model != null && ID.matcher(model.trim()).matches();
    }

    public static String normalize(String model) {
        return model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    public static String join(List<String> models) {
        return String.join(",", models);
    }

    public static List<String> chain(String primary, List<String> fallbacks, boolean allowPaidFallback, boolean structured, int maxFallbacks, Facts facts) {
        if (primary == null || primary.isBlank()) {
            return List.of();
        }
        int room = 1 + Math.min(MAX_FALLBACKS, Math.max(0, maxFallbacks));
        List<String> candidates = new ArrayList<>();
        candidates.add(primary.trim());
        if (fallbacks != null) {
            for (String fallback : fallbacks) {
                if (fallback != null && !fallback.isBlank()) {
                    candidates.add(fallback.trim());
                }
            }
        }
        List<String> chosen = new ArrayList<>();
        for (int index = 0; index < candidates.size() && chosen.size() < room; index++) {
            String model = candidates.get(index);
            if (chosen.stream().anyMatch(existing -> existing.equalsIgnoreCase(model))) {
                continue;
            }
            boolean primarySlot = index == 0;
            if (!primarySlot && isPaid(model) && !allowPaidFallback) {
                continue;
            }
            if (structured && !structuredCompatible(model, primarySlot, facts)) {
                continue;
            }
            chosen.add(model);
        }
        return List.copyOf(chosen);
    }

    public static boolean acceptsFreeResult(String requested, String actual, java.math.BigDecimal cost, Facts facts) {
        if (!isFree(requested)) {
            return true;
        }
        if (cost != null && cost.signum() > 0) {
            return false;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        if (isFree(actual)) {
            return true;
        }
        return facts != null && Boolean.TRUE.equals(facts.zeroPrice(actual));
    }

    private static boolean structuredCompatible(String model, boolean primarySlot, Facts facts) {
        if (tier(model) == Tier.FREE_ROUTER) {
            return true;
        }
        Boolean known = facts == null ? null : facts.supportsStructured(model);
        if (Boolean.TRUE.equals(known)) {
            return true;
        }
        if (Boolean.FALSE.equals(known)) {
            return false;
        }
        return primarySlot;
    }
}

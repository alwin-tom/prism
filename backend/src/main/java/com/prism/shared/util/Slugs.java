package com.prism.shared.util;

import com.prism.shared.exception.ApiException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class Slugs {

    public static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "app", "login", "register", "auth", "assets", "media", "public",
            "static", "health", "render", "account", "events", "templates", "categories",
            "payments", "swagger-ui", "favicon", "robots", "placeholders", "fonts", "customer",
            "dashboard", "review", "reviews", "webhook", "webhooks", "actuator", "v3", "docs",
            "checkout", "settings", "rsvp", "hosting", "about", "privacy", "terms"
    );

    private Slugs() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Choose a URL slug.");
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        validate(slug);
        return slug;
    }

    public static void validate(String slug) {
        if (slug == null || slug.length() < 3 || slug.length() > 48 || !PATTERN.matcher(slug).matches()) {
            throw ApiException.badRequest("Use 3 to 48 letters, numbers and hyphens.");
        }
        if (RESERVED.contains(slug)) {
            throw ApiException.badRequest("That address is reserved.");
        }
    }
}

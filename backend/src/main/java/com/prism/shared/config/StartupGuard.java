package com.prism.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Refuses to stay up in prod with unsafe auth/payment settings.
 * Runs as {@link ApplicationRunner} so a bad config exits the process (Docker/Coolify mark unhealthy).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupGuard implements ApplicationRunner {

    static final String LOCAL_JWT_DEFAULT = "local-only-prism-jwt-secret-change-me";

    private final Environment environment;
    private final PrismProperties properties;

    public StartupGuard(Environment environment, PrismProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        assertSafe(environment, properties);
    }

    public static void assertSafe(Environment environment, PrismProperties properties) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
        if (!production) {
            return;
        }
        // OTP bypass is allowed in prod for temporary staging-like access; turn it off when WhatsApp is live.
        if (properties.isPaymentBypassEnabled()) {
            throw new IllegalStateException("PRISM_PAYMENT_BYPASS_ENABLED must be false when the prod profile is active.");
        }
        String secret = properties.getJwt().getSecret() == null ? "" : properties.getJwt().getSecret().trim();
        if (secret.length() < 32
                || LOCAL_JWT_DEFAULT.equals(secret)
                || secret.startsWith("replace_with")
                || secret.contains("<")
                || secret.contains("≥")) {
            throw new IllegalStateException(
                    "Set JWT_SECRET to a unique secret of at least 32 characters for production (not a placeholder).");
        }
        if (!properties.getSecurity().isCookieSecure()) {
            throw new IllegalStateException("Set PRISM_COOKIE_SECURE=true for production.");
        }
    }
}

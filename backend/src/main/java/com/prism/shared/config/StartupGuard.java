package com.prism.shared.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class StartupGuard {

    static final String LOCAL_JWT_DEFAULT = "local-only-prism-jwt-secret-change-me";

    private final Environment environment;
    private final PrismProperties properties;

    public StartupGuard(Environment environment, PrismProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        assertSafe(environment, properties);
    }

    public static void assertSafe(Environment environment, PrismProperties properties) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
        if (!production) {
            return;
        }
        if (properties.isOtpBypassEnabled()) {
            throw new IllegalStateException("PRISM_OTP_BYPASS_ENABLED must be false when the prod profile is active.");
        }
        if (properties.isPaymentBypassEnabled()) {
            throw new IllegalStateException("PRISM_PAYMENT_BYPASS_ENABLED must be false when the prod profile is active.");
        }
        String secret = properties.getJwt().getSecret() == null ? "" : properties.getJwt().getSecret();
        if (secret.length() < 32 || LOCAL_JWT_DEFAULT.equals(secret) || secret.startsWith("replace_with")) {
            throw new IllegalStateException("Set a unique JWT_SECRET of at least 32 characters for production.");
        }
        if (!properties.getSecurity().isCookieSecure()) {
            throw new IllegalStateException("Set PRISM_COOKIE_SECURE=true for production.");
        }
    }
}

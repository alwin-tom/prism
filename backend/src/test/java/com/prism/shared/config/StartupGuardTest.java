package com.prism.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StartupGuardTest {

    @Test
    void productionRefusesEitherBypass() {
        Environment environment = environment("prod");
        PrismProperties properties = safeProduction();
        properties.setOtpBypassEnabled(true);
        assertThrows(IllegalStateException.class, () -> StartupGuard.assertSafe(environment, properties));
        PrismProperties payment = safeProduction();
        payment.setPaymentBypassEnabled(true);
        assertThrows(IllegalStateException.class, () -> StartupGuard.assertSafe(environment, payment));
    }

    @Test
    void localProfileMayEnableBypasses() {
        PrismProperties properties = new PrismProperties();
        properties.setOtpBypassEnabled(true);
        properties.setPaymentBypassEnabled(true);
        assertDoesNotThrow(() -> StartupGuard.assertSafe(environment("local"), properties));
    }

    @Test
    void productionRefusesPlaceholderJwt() {
        Environment environment = environment("prod");
        PrismProperties properties = safeProduction();
        properties.getJwt().setSecret("<unique ≥32 chars>");
        assertThrows(IllegalStateException.class, () -> StartupGuard.assertSafe(environment, properties));
    }

    private static PrismProperties safeProduction() {
        PrismProperties properties = new PrismProperties();
        properties.getJwt().setSecret("a-real-production-secret-value-32b");
        properties.getSecurity().setCookieSecure(true);
        return properties;
    }

    private static Environment environment(String profile) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{profile});
        return environment;
    }
}

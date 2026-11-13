package com.prism.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpCodesTest {

    @Test
    void generatedCodeIsSixDigitsAndNotTheBypassCode() {
        String code = OtpCodes.generate();
        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
        assertNotEquals(OtpCodes.BYPASS_CODE, code);
    }

    @Test
    void hashMatchesOnlyTheSameCode() {
        String hash = OtpCodes.hash("test-secret-key-must-be-32-characters!!", "919876543210", "482193");
        assertTrue(OtpCodes.matches("test-secret-key-must-be-32-characters!!", "919876543210", "482193", hash));
        assertFalse(OtpCodes.matches("test-secret-key-must-be-32-characters!!", "919876543210", "482194", hash));
    }

    @Test
    void expiryAttemptsAndBypass() {
        assertTrue(OtpCodes.expired(Instant.now().minusSeconds(1), Instant.now()));
        assertFalse(OtpCodes.expired(Instant.now().plusSeconds(30), Instant.now()));
        assertTrue(OtpCodes.attemptsExhausted(5));
        assertFalse(OtpCodes.attemptsExhausted(4));
        assertTrue(OtpCodes.bypassAccepted(true, "111111"));
        assertFalse(OtpCodes.bypassAccepted(false, "111111"));
        assertFalse(OtpCodes.bypassAccepted(true, "482193"));
    }
}

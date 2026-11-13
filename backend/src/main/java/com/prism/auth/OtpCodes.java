package com.prism.auth;

import com.prism.shared.util.Hashes;

import java.security.SecureRandom;
import java.time.Instant;

public final class OtpCodes {

    public static final String BYPASS_CODE = "111111";
    public static final int MAX_ATTEMPTS = 5;
    public static final int TTL_SECONDS = 300;
    public static final int RESEND_SECONDS = 60;
    public static final int HOURLY_PHONE_LIMIT = 5;
    public static final int HOURLY_IP_LIMIT = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpCodes() {
    }

    public static String generate() {
        String code;
        do {
            code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        } while (BYPASS_CODE.equals(code));
        return code;
    }

    public static String hash(String secret, String phone, String code) {
        return Hashes.hmacSha256(secret, phone + ":" + code);
    }

    public static boolean matches(String secret, String phone, String code, String hash) {
        return Hashes.constantTimeEquals(hash(secret, phone, code), hash);
    }

    public static boolean expired(Instant expiresAt, Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public static boolean attemptsExhausted(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    public static boolean bypassAccepted(boolean enabled, String code) {
        return enabled && BYPASS_CODE.equals(code);
    }
}

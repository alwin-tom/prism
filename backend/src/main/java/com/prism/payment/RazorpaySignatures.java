package com.prism.payment;

import com.prism.shared.util.Hashes;

public final class RazorpaySignatures {

    private RazorpaySignatures() {
    }

    public static boolean valid(String secret, String body, String signature) {
        if (secret == null || secret.isBlank() || body == null || signature == null || signature.isBlank()) {
            return false;
        }
        return Hashes.constantTimeEquals(Hashes.hmacSha256(secret, body), signature.trim());
    }
}

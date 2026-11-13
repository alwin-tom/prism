package com.prism.payment;

import com.prism.shared.util.Hashes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpaySignaturesTest {

    @Test
    void acceptsTheRawBodyHmacAndRejectsATamperedBody() {
        String secret = "whsec_test";
        String body = "{\"event\":\"payment.captured\"}";
        String signature = Hashes.hmacSha256(secret, body);
        assertTrue(RazorpaySignatures.valid(secret, body, signature));
        assertFalse(RazorpaySignatures.valid(secret, body + " ", signature));
        assertFalse(RazorpaySignatures.valid("", body, signature));
        assertFalse(RazorpaySignatures.valid(secret, body, null));
    }
}

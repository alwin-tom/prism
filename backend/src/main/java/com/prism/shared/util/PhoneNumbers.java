package com.prism.shared.util;

import com.prism.shared.exception.ApiException;

public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Enter a mobile number.");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 10) {
            digits = "91" + digits;
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = "91" + digits.substring(1);
        }
        if (digits.length() < 11 || digits.length() > 15) {
            throw ApiException.badRequest("Enter a mobile number with its country code.");
        }
        return digits;
    }

    public static String display(String normalized) {
        if (normalized != null && normalized.startsWith("91") && normalized.length() == 12) {
            return "+91 " + normalized.substring(2, 7) + " " + normalized.substring(7);
        }
        return normalized == null ? "" : "+" + normalized;
    }
}

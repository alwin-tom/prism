package com.prism.shared.util;

import java.util.Locale;

public final class Money {

    private Money() {
    }

    public static String formatPaise(int paise) {
        int rupees = paise / 100;
        int remainder = Math.abs(paise % 100);
        if (remainder == 0) {
            return "₹" + String.format(Locale.ENGLISH, "%,d", rupees);
        }
        return "₹" + String.format(Locale.ENGLISH, "%,d.%02d", rupees, remainder);
    }
}

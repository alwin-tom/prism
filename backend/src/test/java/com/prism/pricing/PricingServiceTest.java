package com.prism.pricing;

import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingServiceTest {

    @Test
    void sevenDaysIsThreeThousandFiveHundredRupees() {
        PrismProperties properties = new PrismProperties();
        properties.setDailyHostingPricePaise(50_000);
        properties.setDefaultCurrency("INR");
        PricingService.Quote quote = new PricingService(properties).quote(7);
        assertEquals(7, quote.days());
        assertEquals(50_000, quote.dailyRatePaise());
        assertEquals(350_000, quote.amountPaise());
        assertEquals("₹3,500", quote.formatted());
    }

    @Test
    void rejectsAZeroDayQuote() {
        assertThrows(ApiException.class, () -> new PricingService(new PrismProperties()).quote(0));
    }
}

package com.prism.pricing;

import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.util.Money;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

    private final PrismProperties properties;

    public PricingService(PrismProperties properties) {
        this.properties = properties;
    }

    public Quote quote(int days) {
        if (days < 1 || days > 365) {
            throw ApiException.badRequest("Choose between 1 and 365 hosting days.");
        }
        int rate = properties.getDailyHostingPricePaise();
        if (rate < 1) {
            throw ApiException.unavailable("Hosting is not priced yet.");
        }
        long amount = (long) days * rate;
        if (amount > Integer.MAX_VALUE) {
            throw ApiException.badRequest("That hosting period is too long to price.");
        }
        return new Quote(days, rate, (int) amount, properties.getDefaultCurrency(), Money.formatPaise((int) amount));
    }

    public record Quote(int days, int dailyRatePaise, int amountPaise, String currency, String formatted) {
    }
}

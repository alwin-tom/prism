package com.prism.payment;

import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class RazorpayClient {

    private final PrismProperties properties;
    private final RestClient restClient;

    public RazorpayClient(PrismProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public boolean enabled() {
        PrismProperties.Razorpay razorpay = properties.getRazorpay();
        return razorpay.isEnabled()
                && razorpay.getKeyId() != null && !razorpay.getKeyId().isBlank()
                && razorpay.getKeySecret() != null && !razorpay.getKeySecret().isBlank();
    }

    public String createOrder(int amountPaise, String currency, String receipt) {
        if (!enabled()) {
            throw ApiException.unavailable("Razorpay is not configured.");
        }
        PrismProperties.Razorpay razorpay = properties.getRazorpay();
        String credentials = razorpay.getKeyId() + ":" + razorpay.getKeySecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        try {
            Map<?, ?> response = restClient.post()
                    .uri("https://api.razorpay.com/v1/orders")
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", amountPaise,
                            "currency", currency,
                            "receipt", receipt))
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("id") == null) {
                throw ApiException.unavailable("Razorpay did not return an order.");
            }
            return response.get("id").toString();
        } catch (RestClientException exception) {
            throw ApiException.unavailable("Razorpay could not open the order.");
        }
    }
}

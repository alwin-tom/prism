package com.prism.creation;

import com.prism.shared.config.PrismProperties;

import java.math.BigDecimal;

public final class AttemptBudget {

    private final int maxRequests;
    private final BigDecimal maxCost;
    private int requests;
    private long latencyMs;
    private BigDecimal cost = BigDecimal.ZERO;

    private AttemptBudget(int maxRequests, BigDecimal maxCost) {
        this.maxRequests = maxRequests;
        this.maxCost = maxCost == null ? BigDecimal.ZERO : maxCost;
    }

    public static AttemptBudget unlimited() {
        return new AttemptBudget(0, BigDecimal.ZERO);
    }

    public static AttemptBudget from(PrismProperties.OpenRouter settings) {
        int requests = settings.getMaxRequestsPerGeneration();
        if (requests <= 0) {
            requests = 8;
        }
        return new AttemptBudget(Math.min(OpenRouterModels.MAX_REQUESTS, requests), settings.getMaxCostPerGenerationUsd());
    }

    public void beforeCall() {
        if (maxRequests > 0 && requests >= maxRequests) {
            throw new OpenRouterException(OpenRouterException.Kind.BUDGET, "This generation reached its request limit.");
        }
        if (maxCost.signum() > 0 && cost.compareTo(maxCost) >= 0) {
            throw new OpenRouterException(OpenRouterException.Kind.BUDGET, "This generation reached its cost limit.");
        }
        requests++;
    }

    public void add(BigDecimal spent, long latency) {
        if (spent != null && spent.signum() > 0) {
            cost = cost.add(spent);
        }
        if (latency > 0) {
            latencyMs += latency;
        }
    }

    public int requests() {
        return requests;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public BigDecimal cost() {
        return cost;
    }
}

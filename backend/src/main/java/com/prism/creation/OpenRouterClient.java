package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public interface OpenRouterClient {

    Completion complete(String model, String system, String user, JsonNode schema, String schemaName);

    default Completion complete(Route route, String system, String user, JsonNode schema, String schemaName) {
        return complete(route.model(), system, user, schema, schemaName);
    }

    record Route(String model, List<String> fallbacks, boolean allowPaidFallback, int maxRetries, int maxFallbacks, AttemptBudget budget) {
        public static Route only(String model, int maxRetries) {
            return new Route(model, List.of(), false, maxRetries, 0, AttemptBudget.unlimited());
        }
    }

    record Completion(JsonNode content, int inputTokens, int outputTokens, String model, boolean structured, BigDecimal costUsd, long latencyMs, String requestedModel) {
        public Completion(JsonNode content, int inputTokens, int outputTokens, String model, boolean structured) {
            this(content, inputTokens, outputTokens, model, structured, null, 0L, model);
        }
    }
}

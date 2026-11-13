package com.prism.designlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.config.PrismProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
public class OpenRouterEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterEmbeddingClient.class);

    private final PrismProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public OpenRouterEmbeddingClient(PrismProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public boolean available() {
        PrismProperties.Creation creation = properties.getCreation();
        return creation.isDesignEmbeddingsEnabled()
                && "openrouter".equalsIgnoreCase(blank(creation.getDesignEmbeddingProvider()))
                && !blank(properties.getOpenrouter().getApiKey()).isBlank()
                && !blank(creation.getDesignEmbeddingModel()).isBlank();
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (!available() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", modelName());
            body.put("input", text.length() > 8000 ? text.substring(0, 8000) : text);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(properties.getOpenrouter().getBaseUrl()) + "/embeddings"))
                    .timeout(Duration.ofSeconds(Math.max(20, properties.getOpenrouter().getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getOpenrouter().getApiKey())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", properties.getPublicBaseUrl())
                    .header("X-OpenRouter-Title", "WebAux")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Embedding request failed status={}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode vector = root.path("data").path(0).path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                return Optional.empty();
            }
            float[] values = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                values[i] = (float) vector.get(i).asDouble();
            }
            return Optional.of(values);
        } catch (Exception exception) {
            log.warn("Embedding request failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    @Override
    public String modelName() {
        return blank(properties.getCreation().getDesignEmbeddingModel());
    }

    @Override
    public int dimensions() {
        return Math.max(8, properties.getCreation().getDesignEmbeddingDimensions());
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimSlash(String value) {
        String base = blank(value);
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}

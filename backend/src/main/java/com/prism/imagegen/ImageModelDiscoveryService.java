package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.shared.config.PrismProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ImageModelDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ImageModelDiscoveryService.class);

    private final PrismProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    private volatile List<ImageModelCard> cache = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;
    private volatile String cacheBase = "";

    public ImageModelDiscoveryService(PrismProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public List<ImageModelCard> list(boolean forceRefresh) {
        if (!forceRefresh && !cache.isEmpty() && !expired()) {
            return cache;
        }
        synchronized (this) {
            if (!forceRefresh && !cache.isEmpty() && !expired()) {
                return cache;
            }
            List<ImageModelCard> next = fetch();
            cache = List.copyOf(next);
            loadedAt = Instant.now();
            cacheBase = baseUrl();
            return cache;
        }
    }

    public List<ImageModelCard> list() {
        return list(false);
    }

    private boolean expired() {
        long minutes = Math.max(1, properties.getImage().getModelCacheMinutes());
        return Instant.now().isAfter(loadedAt.plusSeconds(minutes * 60L)) || !baseUrl().equals(cacheBase);
    }

    private List<ImageModelCard> fetch() {
        PrismProperties.OpenRouter settings = properties.getOpenrouter();
        String base = baseUrl();
        Map<String, BigDecimal> prices = fetchPrices(base, settings);
        List<ImageModelCard> cards = new ArrayList<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/images/models"))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (settings.getApiKey() != null && !settings.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + settings.getApiKey());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Image model discovery failed with status {}", response.statusCode());
                return List.of();
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            for (JsonNode item : data) {
                String id = item.path("id").asText("").trim().toLowerCase(Locale.ROOT);
                if (id.isBlank()) {
                    continue;
                }
                JsonNode architecture = item.path("architecture");
                boolean outputsImage = false;
                for (JsonNode modality : architecture.path("output_modalities")) {
                    if ("image".equalsIgnoreCase(modality.asText())) {
                        outputsImage = true;
                        break;
                    }
                }
                if (!outputsImage && architecture.path("output_modalities").isMissingNode()) {
                    outputsImage = true;
                }
                if (!outputsImage) {
                    continue;
                }
                Set<String> aspects = new LinkedHashSet<>();
                Set<String> resolutions = new LinkedHashSet<>();
                Set<String> parameters = new LinkedHashSet<>();
                JsonNode supported = item.path("supported_parameters");
                supported.fieldNames().forEachRemaining(parameters::add);
                JsonNode aspectNode = supported.path("aspect_ratio");
                for (JsonNode value : aspectNode.path("values")) {
                    aspects.add(value.asText());
                }
                JsonNode resolutionNode = supported.path("resolution");
                for (JsonNode value : resolutionNode.path("values")) {
                    resolutions.add(value.asText());
                }
                String name = item.path("name").asText(id);
                String description = item.path("description").asText("");
                String freeId = id.endsWith(":free") ? id : id + ":free";
                BigDecimal freePrice = prices.get(freeId);
                if (freePrice != null && freePrice.compareTo(BigDecimal.ZERO) == 0) {
                    cards.add(new ImageModelCard(
                            freeId,
                            name + " (free)",
                            description,
                            aspects,
                            resolutions,
                            parameters,
                            BigDecimal.ZERO,
                            true,
                            true
                    ));
                }
                BigDecimal price = prices.get(id);
                boolean priceKnown = price != null;
                boolean free = (priceKnown && price.compareTo(BigDecimal.ZERO) == 0)
                        || id.endsWith(":free")
                        || id.contains("/free");
                if (!priceKnown && (id.endsWith(":free") || id.contains("/free"))) {
                    price = BigDecimal.ZERO;
                    priceKnown = true;
                    free = true;
                }
                if (!free || !freeId.equals(id)) {
                    cards.add(new ImageModelCard(
                            id,
                            name,
                            description,
                            aspects,
                            resolutions,
                            parameters,
                            price,
                            priceKnown,
                            free
                    ));
                }
            }
        } catch (Exception exception) {
            log.warn("Image model discovery failed: {}", exception.toString());
            return List.of();
        }
        return cards;
    }

    private Map<String, BigDecimal> fetchPrices(String base, PrismProperties.OpenRouter settings) {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/models?output_modalities=image"))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (settings.getApiKey() != null && !settings.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + settings.getApiKey());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return prices;
            }
            JsonNode data = mapper.readTree(response.body()).path("data");
            if (!data.isArray()) {
                return prices;
            }
            for (JsonNode item : data) {
                String id = item.path("id").asText("").trim().toLowerCase(Locale.ROOT);
                if (id.isBlank()) {
                    continue;
                }
                JsonNode pricing = item.path("pricing");
                BigDecimal imagePrice = firstPrice(pricing, "image_output", "image_token", "image", "completion");
                if (imagePrice != null) {
                    prices.put(id, imagePrice);
                }
            }
        } catch (Exception exception) {
            log.debug("Image model pricing lookup failed: {}", exception.toString());
        }
        return prices;
    }

    private static BigDecimal firstPrice(JsonNode pricing, String... keys) {
        if (pricing == null || pricing.isMissingNode() || pricing.isNull()) {
            return null;
        }
        for (String key : keys) {
            BigDecimal value = decimal(pricing.path(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText("").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String baseUrl() {
        String base = properties.getOpenrouter().getBaseUrl();
        if (base == null || base.isBlank()) {
            return "https://openrouter.ai/api/v1";
        }
        return base.replaceAll("/+$", "");
    }
}

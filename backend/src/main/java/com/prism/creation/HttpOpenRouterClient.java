package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.config.PrismProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class HttpOpenRouterClient implements OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOpenRouterClient.class);

    private final PrismProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private volatile Map<String, ModelCard> catalog = Map.of();
    private volatile long catalogLoadedAt;
    private volatile String catalogBase = "";
    private volatile Map<String, String> providerSlugs = Map.of();

    public HttpOpenRouterClient(PrismProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        int timeout = Math.max(10, properties.getOpenrouter().getTimeoutSeconds());
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(20, timeout))).build();
    }

    @Override
    public Completion complete(String model, String system, String user, JsonNode schema, String schemaName) {
        PrismProperties.OpenRouter settings = properties.getOpenrouter();
        return complete(Route.only(model, settings.getMaxRetries()), system, user, schema, schemaName);
    }

    @Override
    public Completion complete(Route route, String system, String user, JsonNode schema, String schemaName) {
        PrismProperties.OpenRouter settings = properties.getOpenrouter();
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new OpenRouterException(OpenRouterException.Kind.AUTH, "Website generation is not configured.");
        }
        OpenRouterModels.Facts facts = catalog();
        boolean wantStructured = schema != null;
        List<String> models = OpenRouterModels.chain(
                route.model(),
                route.fallbacks(),
                route.allowPaidFallback(),
                wantStructured,
                route.maxFallbacks(),
                facts
        );
        if (models.isEmpty()) {
            throw new OpenRouterException(
                    wantStructured ? OpenRouterException.Kind.STRUCTURED : OpenRouterException.Kind.AUTH,
                    wantStructured ? "No configured model supports structured output." : "Website generation is not configured."
            );
        }
        OpenRouterException last = null;
        int retries = Math.min(OpenRouterModels.MAX_RETRIES, Math.max(0, route.maxRetries()));
        for (String model : models) {
            boolean structured = wantStructured;
            boolean relaxed = false;
            boolean lengthRetried = false;
            int maxTokens = settings.getMaxOutputTokens();
            int transientFailures = 0;
            String effort = null;
            String ignoredProvider = null;
            boolean restoredReasoning = false;
            while (true) {
                long started = System.nanoTime();
                try {
                    route.budget().beforeCall();
                    Completion completion = send(settings, model, system, user, structured ? schema : null, schemaName, structured, maxTokens, effort, ignoredProvider);
                    long latency = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
                    route.budget().add(completion.costUsd(), latency);
                    if (!OpenRouterModels.acceptsFreeResult(model, completion.model(), completion.costUsd(), facts)) {
                        last = new OpenRouterException(OpenRouterException.Kind.PAID, "A free model returned a paid result. That result was discarded.");
                        break;
                    }
                    return new Completion(completion.content(), completion.inputTokens(), completion.outputTokens(), completion.model(), completion.structured(), completion.costUsd(), latency, model);
                } catch (OpenRouterException exception) {
                    route.budget().add(null, Math.max(0, (System.nanoTime() - started) / 1_000_000L));
                    last = exception;
                    if (exception.kind() == OpenRouterException.Kind.BUDGET || exception.kind() == OpenRouterException.Kind.AUTH || exception.kind() == OpenRouterException.Kind.CONTEXT) {
                        throw exception;
                    }
                    if (exception.kind() == OpenRouterException.Kind.STRUCTURED && structured && !relaxed) {
                        structured = false;
                        relaxed = true;
                        continue;
                    }
                    if (exception.reasoningLocked()) {
                        if ("none".equals(effort) && !restoredReasoning) {
                            restoredReasoning = true;
                            effort = null;
                            ignoredProvider = null;
                            continue;
                        }
                        break;
                    }
                    if (exception.kind() == OpenRouterException.Kind.LENGTH && !lengthRetried) {
                        lengthRetried = true;
                        if (exception.reasoningOverBudget()) {
                            effort = "none";
                            ignoredProvider = providerSlug(settings, exception.provider());
                        } else {
                            maxTokens = Math.min(32000, Math.max(maxTokens + 1, maxTokens * 2));
                        }
                        continue;
                    }
                    if (retryable(exception.kind()) && transientFailures < retries) {
                        transientFailures++;
                        pause(transientFailures);
                        continue;
                    }
                    break;
                }
            }
        }
        throw last == null ? new OpenRouterException(OpenRouterException.Kind.PROVIDER, "The model provider returned an error.") : last;
    }

    private Completion send(PrismProperties.OpenRouter settings, String model, String system, String user, JsonNode schema, String schemaName, boolean structured, int maxTokens, String effort, String ignoredProvider) {
        ObjectNode body = OpenRouterMessages.body(mapper, model, system, user, schema, schemaName, maxTokens, structured);
        if (effort != null && !effort.isBlank()) {
            ObjectNode reasoning = body.with("reasoning");
            reasoning.remove("max_tokens");
            reasoning.put("effort", effort);
        }
        if (ignoredProvider != null && !ignoredProvider.isBlank()) {
            body.with("provider").putArray("ignore").add(ignoredProvider);
        }
        URI uri = endpoint(settings.getBaseUrl());
        String payload = body.toString();
        log.info("OpenRouter request POST {} body={}", uri, redact(payload));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(10, settings.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", properties.getPublicBaseUrl())
                .header("X-OpenRouter-Title", "WebAux")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("OpenRouter request failed POST {}", uri);
            throw new OpenRouterException(OpenRouterException.Kind.TIMEOUT, "Website generation timed out.");
        } catch (Exception exception) {
            log.warn("OpenRouter request failed POST {}", uri);
            throw new OpenRouterException(OpenRouterException.Kind.TIMEOUT, "Website generation timed out.");
        }
        log.info("OpenRouter response POST {} status={} body={}", uri, response.statusCode(), redact(response.body()));
        return read(response, structured);
    }

    private Completion read(HttpResponse<String> response, boolean structured) {
        int status = response.statusCode();
        JsonNode payload = parse(response.body());
        if (payload != null && payload.path("error").isObject() && status < 400) {
            int code = payload.path("error").path("code").asInt(400);
            if (code >= 400) {
                status = code;
            }
        }
        if (status == 401 || status == 403) {
            throw new OpenRouterException(OpenRouterException.Kind.AUTH, "Website generation is not configured.");
        }
        if (status == 429) {
            throw new OpenRouterException(OpenRouterException.Kind.RATE, "The model provider is busy. Try again shortly.");
        }
        if (status == 400 && reasoningLocked(payload, response.body())) {
            throw new OpenRouterException(OpenRouterException.Kind.PROVIDER, shortError(payload), false, "", true);
        }
        if (status == 400 && structured && structuredRejected(payload, response.body())) {
            throw new OpenRouterException(OpenRouterException.Kind.STRUCTURED, "Structured output is not available for this model.");
        }
        if (status == 400 && context(payload)) {
            throw new OpenRouterException(OpenRouterException.Kind.CONTEXT, "The request is too large for the model.");
        }
        if (status >= 500 || status == 408) {
            throw new OpenRouterException(OpenRouterException.Kind.PROVIDER, "The model provider returned an error.");
        }
        if (status >= 400) {
            throw new OpenRouterException(OpenRouterException.Kind.PROVIDER, shortError(payload));
        }
        JsonNode message = payload.path("choices").path(0).path("message");
        String finish = payload.path("choices").path(0).path("finish_reason").asText("");
        String content = contentText(message);
        if (content.isBlank()) {
            if ("length".equals(finish)) {
                int reasoning = payload.path("usage").path("completion_tokens_details").path("reasoning_tokens").asInt(0);
                throw new OpenRouterException(
                        OpenRouterException.Kind.LENGTH,
                        "The model used its output budget before writing the page.",
                        reasoning > 1024,
                        payload.path("provider").asText("")
                );
            }
            throw new OpenRouterException(OpenRouterException.Kind.EMPTY, "The model returned an empty response.");
        }
        JsonNode json = jsonObject(content);
        if (json == null) {
            if ("length".equals(finish)) {
                throw new OpenRouterException(OpenRouterException.Kind.LENGTH, "The model used its output budget before writing the page.");
            }
            throw new OpenRouterException(OpenRouterException.Kind.MALFORMED, "The model did not return a JSON object.");
        }
        int input = payload.path("usage").path("prompt_tokens").asInt(0);
        int output = payload.path("usage").path("completion_tokens").asInt(0);
        BigDecimal cost = PrismSchemaCheck.money(payload.path("usage").path("cost"));
        if (cost == null) {
            cost = PrismSchemaCheck.money(payload.path("usage").path("total_cost"));
        }
        return new Completion(json, input, output, payload.path("model").asText(""), structured, cost, 0L, "");
    }

    private OpenRouterModels.Facts catalog() {
        PrismProperties.OpenRouter settings = properties.getOpenrouter();
        String base = settings.getBaseUrl() == null ? "" : settings.getBaseUrl();
        long now = System.currentTimeMillis();
        if (base.equals(catalogBase) && catalogLoadedAt > 0 && now - catalogLoadedAt < 600_000) {
            return facts();
        }
        catalog = loadCatalog(settings);
        catalogBase = base;
        catalogLoadedAt = now;
        return facts();
    }

    private OpenRouterModels.Facts facts() {
        Map<String, ModelCard> snapshot = catalog;
        return new OpenRouterModels.Facts() {
            @Override
            public Boolean supportsStructured(String model) {
                ModelCard card = snapshot.get(OpenRouterModels.normalize(model));
                return card == null ? null : card.structured;
            }

            @Override
            public Boolean zeroPrice(String model) {
                ModelCard card = snapshot.get(OpenRouterModels.normalize(model));
                return card == null ? null : card.freePrice;
            }
        };
    }

    private String providerSlug(PrismProperties.OpenRouter settings, String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        Map<String, String> slugs = providerSlugs;
        if (slugs.isEmpty()) {
            slugs = loadProviderSlugs(settings);
            providerSlugs = slugs;
        }
        return slugs.getOrDefault(name.toLowerCase(Locale.ROOT), "");
    }

    private Map<String, String> loadProviderSlugs(PrismProperties.OpenRouter settings) {
        URI uri = URI.create(root(settings.getBaseUrl()) + "/providers");
        log.info("OpenRouter request GET {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("HTTP-Referer", properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl())
                .header("X-OpenRouter-Title", "WebAux")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("OpenRouter response GET {} status={} bytes={}", uri, response.statusCode(), response.body() == null ? 0 : response.body().length());
            if (response.statusCode() != 200) {
                return Map.of();
            }
            JsonNode data = parse(response.body());
            if (data == null) {
                return Map.of();
            }
            Map<String, String> slugs = new HashMap<>();
            for (JsonNode provider : data.path("data")) {
                String providerName = provider.path("name").asText("");
                String slug = provider.path("slug").asText("");
                if (!providerName.isBlank() && !slug.isBlank()) {
                    slugs.put(providerName.toLowerCase(Locale.ROOT), slug);
                }
            }
            return slugs;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("OpenRouter request failed GET {}", uri);
            return Map.of();
        } catch (Exception exception) {
            log.warn("OpenRouter request failed GET {}", uri);
            return Map.of();
        }
    }

    private Map<String, ModelCard> loadCatalog(PrismProperties.OpenRouter settings) {
        URI uri = URI.create(root(settings.getBaseUrl()) + "/models");
        log.info("OpenRouter request GET {}", uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("HTTP-Referer", properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl())
                .header("X-OpenRouter-Title", "WebAux")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("OpenRouter response GET {} status={} bytes={}", uri, response.statusCode(), response.body() == null ? 0 : response.body().length());
            if (response.statusCode() != 200) {
                return Map.of();
            }
            JsonNode data = parse(response.body());
            if (data == null) {
                return Map.of();
            }
            Map<String, ModelCard> cards = new HashMap<>();
            for (JsonNode model : data.path("data")) {
                String id = OpenRouterModels.normalize(model.path("id").asText(""));
                if (id.isBlank()) {
                    continue;
                }
                boolean structured = false;
                for (JsonNode parameter : model.path("supported_parameters")) {
                    String name = parameter.asText("");
                    if ("structured_outputs".equals(name) || "response_format".equals(name)) {
                        structured = true;
                    }
                }
                BigDecimal prompt = PrismSchemaCheck.money(model.path("pricing").path("prompt"));
                BigDecimal completion = PrismSchemaCheck.money(model.path("pricing").path("completion"));
                boolean free = prompt != null && completion != null && prompt.signum() == 0 && completion.signum() == 0;
                cards.put(id, new ModelCard(structured, free));
            }
            return cards;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("OpenRouter request failed GET {}", uri);
            return Map.of();
        } catch (Exception exception) {
            log.warn("OpenRouter request failed GET {}", uri);
            return Map.of();
        }
    }

    private String root(String base) {
        return base == null || base.isBlank() ? "https://openrouter.ai/api/v1" : base.replaceAll("/+$", "");
    }

    private record ModelCard(boolean structured, boolean freePrice) {
    }

    private String contentText(JsonNode message) {
        JsonNode content = message.path("content");
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                text.append(part.isTextual() ? part.asText("") : part.path("text").asText(""));
            }
            return text.toString();
        }
        return content.asText("");
    }

    private JsonNode jsonObject(String content) {
        String trimmed = content.trim();
        JsonNode direct = readTree(trimmed);
        if (direct != null && direct.isObject()) {
            return direct;
        }
        String unfenced = fence(trimmed);
        if (!unfenced.equals(trimmed)) {
            JsonNode fromFence = readTree(unfenced);
            if (fromFence != null && fromFence.isObject()) {
                return fromFence;
            }
            trimmed = unfenced;
        }
        if (trimmed.startsWith("[")) {
            return null;
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JsonNode sliced = readTree(trimmed.substring(start, end + 1));
            if (sliced != null && sliced.isObject()) {
                return sliced;
            }
        }
        return null;
    }

    private String fence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstBreak = trimmed.indexOf('\n');
        if (firstBreak < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstBreak + 1);
        int close = body.lastIndexOf("```");
        if (close >= 0) {
            body = body.substring(0, close);
        }
        return body.trim();
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception exception) {
            return null;
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return mapper.createObjectNode();
        }
        return readTree(body);
    }

    private boolean reasoningLocked(JsonNode payload, String raw) {
        String text = ((payload == null ? "" : payload.toString()) + " " + (raw == null ? "" : raw)).toLowerCase();
        return text.contains("reasoning is mandatory") || text.contains("cannot be disabled");
    }

    private boolean structuredRejected(JsonNode payload, String raw) {
        String text = ((payload == null ? "" : payload.toString()) + " " + (raw == null ? "" : raw)).toLowerCase();
        return text.contains("response_format") || text.contains("json_schema") || text.contains("structured");
    }

    private boolean context(JsonNode payload) {
        String text = payload == null ? "" : payload.toString().toLowerCase();
        return text.contains("context") || text.contains("maximum") && text.contains("token");
    }

    private String shortError(JsonNode payload) {
        String message = payload == null ? "" : payload.path("error").path("message").asText("");
        if (message.isBlank() || message.length() > 180 || message.toLowerCase().contains("bearer")) {
            return "The model provider returned an error.";
        }
        return message;
    }

    private boolean retryable(OpenRouterException.Kind kind) {
        return kind == OpenRouterException.Kind.RATE || kind == OpenRouterException.Kind.PROVIDER || kind == OpenRouterException.Kind.TIMEOUT;
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(Math.min(2000L, 400L * attempt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private URI endpoint(String base) {
        return URI.create(root(base) + "/chat/completions");
    }

    private String redact(String value) {
        if (value == null) {
            return "";
        }
        String key = properties.getOpenrouter().getApiKey();
        if (key == null || key.isBlank()) {
            return value;
        }
        return value.replace(key, "[redacted]");
    }
}

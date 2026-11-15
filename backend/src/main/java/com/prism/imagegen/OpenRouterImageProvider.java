package com.prism.imagegen;

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
import java.util.Base64;
import java.util.Locale;

@Component
public class OpenRouterImageProvider implements ImageProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterImageProvider.class);

    private final PrismProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenRouterImageProvider(PrismProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        int timeout = Math.max(30, properties.getOpenrouter().getTimeoutSeconds());
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(20, timeout))).build();
    }

    @Override
    public String id() {
        return "openrouter";
    }

    @Override
    public GeneratedImage generate(Request request) {
        PrismProperties.OpenRouter settings = properties.getOpenrouter();
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new ImageProviderException(ImageProviderException.Kind.AUTH, "Image generation is not configured.");
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new ImageProviderException(ImageProviderException.Kind.INVALID, "No image model was selected.");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ImageProviderException(ImageProviderException.Kind.INVALID, "The image prompt is empty.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("prompt", request.prompt().trim());
        if (request.includeAspectRatio() && request.aspectRatio() != null && !request.aspectRatio().isBlank()) {
            body.put("aspect_ratio", request.aspectRatio());
        }
        if (request.includeOutputFormat()) {
            body.put("output_format", request.outputFormat() == null || request.outputFormat().isBlank() ? "png" : request.outputFormat());
        }
        if (request.includeN()) {
            body.put("n", 1);
        }

        String base = settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()
                ? "https://openrouter.ai/api/v1"
                : settings.getBaseUrl().replaceAll("/+$", "");
        int timeout = Math.max(30, settings.getTimeoutSeconds());
        int retries = Math.max(0, properties.getImage().getMaxRetries());
        ImageProviderException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            long started = System.nanoTime();
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(base + "/images"))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Authorization", "Bearer " + settings.getApiKey())
                        .header("Content-Type", "application/json")
                        .header("HTTP-Referer", properties.getPublicBaseUrl())
                        .header("X-OpenRouter-Title", "WebAux")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int latency = (int) Math.max(0, (System.nanoTime() - started) / 1_000_000L);
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new ImageProviderException(ImageProviderException.Kind.AUTH, "Image generation is not authorised.");
                }
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    last = new ImageProviderException(ImageProviderException.Kind.PROVIDER, "The image provider is temporarily unavailable.");
                    sleep(attempt);
                    continue;
                }
                if (response.statusCode() >= 400) {
                    String detail = safeError(response.body());
                    log.warn("Image provider rejected model {} with status {}: {}", request.model(), response.statusCode(), detail);
                    throw new ImageProviderException(ImageProviderException.Kind.PROVIDER, detail);
                }
                return parse(response.body(), request.model(), latency);
            } catch (ImageProviderException exception) {
                throw exception;
            } catch (java.net.http.HttpTimeoutException exception) {
                last = new ImageProviderException(ImageProviderException.Kind.TIMEOUT, "The image provider timed out.", exception);
                sleep(attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ImageProviderException(ImageProviderException.Kind.TIMEOUT, "Image generation was interrupted.", exception);
            } catch (Exception exception) {
                last = new ImageProviderException(ImageProviderException.Kind.PROVIDER, "The image could not be generated.", exception);
                log.warn("Image generation attempt failed for model {}: {}", request.model(), exception.toString());
                sleep(attempt);
            }
        }
        throw last == null
                ? new ImageProviderException(ImageProviderException.Kind.PROVIDER, "The image could not be generated.")
                : last;
    }

    private GeneratedImage parse(String body, String requestedModel, int latency) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new ImageProviderException(ImageProviderException.Kind.INVALID, "The image provider returned no image data.");
        }
        JsonNode first = data.get(0);
        String b64 = first.path("b64_json").asText("");
        if (b64.isBlank()) {
            String url = first.path("url").asText("");
            if (!url.isBlank()) {
                throw new ImageProviderException(ImageProviderException.Kind.INVALID, "URL-only image responses are not supported.");
            }
            throw new ImageProviderException(ImageProviderException.Kind.INVALID, "The image provider returned an empty payload.");
        }
        byte[] bytes;
        try {
            String cleaned = b64;
            int comma = cleaned.indexOf(',');
            if (cleaned.startsWith("data:") && comma > 0) {
                cleaned = cleaned.substring(comma + 1);
            }
            bytes = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException exception) {
            throw new ImageProviderException(ImageProviderException.Kind.INVALID, "The image payload was not valid Base64.", exception);
        }
        BigDecimal cost = null;
        JsonNode usage = root.path("usage");
        if (usage.has("cost") && usage.path("cost").isNumber()) {
            cost = usage.path("cost").decimalValue();
        }
        String model = root.path("model").asText(requestedModel);
        String mime = sniffMime(bytes);
        return new GeneratedImage(bytes, mime, model, cost, latency);
    }

    private static String sniffMime(byte[] bytes) {
        try {
            return MediaServiceDetect.mime(bytes);
        } catch (RuntimeException ignored) {
            return "image/png";
        }
    }

    private static String safeError(String body) {
        if (body == null || body.isBlank()) {
            return "The image provider rejected the request.";
        }
        String lowered = body.toLowerCase(Locale.ROOT);
        if (lowered.contains("api key") || lowered.contains("authorization") || lowered.contains("bearer")) {
            return "Image generation is not authorised.";
        }
        try {
            JsonNode root = new ObjectMapper().readTree(body);
            String message = root.path("error").path("message").asText(root.path("message").asText("")).trim();
            if (!message.isBlank()) {
                if (message.length() > 220) {
                    message = message.substring(0, 220);
                }
                return message;
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return "The image provider rejected the request.";
    }

    private static void sleep(int attempt) {
        if (attempt <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(8_000L, 500L * (1L << Math.min(4, attempt))));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Local helper so ImageProvider does not depend on MediaService package visibility for MIME hints. */
    static final class MediaServiceDetect {
        private MediaServiceDetect() {
        }

        static String mime(byte[] bytes) {
            return com.prism.media.MediaService.detectBytes(bytes, "image.png").mime();
        }
    }
}

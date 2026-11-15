package com.prism.imagegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.shared.config.PrismProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterImageProviderTest {

    private HttpServer server;
    private PrismProperties properties;
    private OpenRouterImageProvider provider;
    private byte[] png;

    @BeforeEach
    void setUp() throws IOException {
        png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0, 0, 0, 0, 0
        };
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        properties = new PrismProperties();
        properties.getOpenrouter().setApiKey("test-key");
        properties.getOpenrouter().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
        properties.getOpenrouter().setTimeoutSeconds(5);
        properties.getImage().setMaxRetries(0);
        provider = new OpenRouterImageProvider(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void decodesSuccessfulImage() {
        server.createContext("/api/v1/images", exchange -> {
            String body = """
                    {"created":1,"data":[{"b64_json":"%s"}],"usage":{"cost":0.02},"model":"test/model"}
                    """.formatted(Base64.getEncoder().encodeToString(png));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        ImageProvider.GeneratedImage image = provider.generate(new ImageProvider.Request("test/model", "soft light", "16:9", "png"));
        assertEquals(png.length, image.bytes().length);
        assertEquals(0, image.costUsd().compareTo(new BigDecimal("0.02")));
    }

    @Test
    void rejectsInvalidPayload() {
        server.createContext("/api/v1/images", exchange -> {
            byte[] bytes = "{\"data\":[{\"b64_json\":\"!!!\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        ImageProviderException exception = assertThrows(
                ImageProviderException.class,
                () -> provider.generate(new ImageProvider.Request("test/model", "soft light", "16:9", "png"))
        );
        assertEquals(ImageProviderException.Kind.INVALID, exception.kind());
        assertTrue(exception.getMessage().toLowerCase().contains("base64") || exception.getMessage().toLowerCase().contains("payload"));
    }

    @Test
    void omitsUnsupportedOutputFormat() throws Exception {
        final String[] seen = {""};
        server.createContext("/api/v1/images", exchange -> {
            seen[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = """
                    {"created":1,"data":[{"b64_json":"%s"}],"usage":{"cost":0.01},"model":"test/model"}
                    """.formatted(Base64.getEncoder().encodeToString(png));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        provider.generate(new ImageProvider.Request("test/model", "soft light", "16:9", "png", true, false, false));
        assertTrue(seen[0].contains("aspect_ratio"));
        assertTrue(!seen[0].contains("output_format"));
        assertTrue(!seen[0].contains("\"n\""));
    }
}

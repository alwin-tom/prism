package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.shared.config.PrismProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOpenRouterClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fallsBackWhenStructuredOutputIsRejected() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(400, 200), List.of(
                "{\"error\":{\"message\":\"response_format is not supported\"}}",
                "{\"model\":\"fallback\",\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"Ada\\\"}\"}}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3}}"
        ));
        try {
            OpenRouterClient.Completion completion = client(server, 0).complete("model", "system", "answers", PrismSchemas.plan(mapper), PrismSchemas.PLAN_SCHEMA);
            assertEquals("Ada", completion.content().path("title").asText());
            assertEquals(2, bodies.size());
            assertEquals("json_schema", mapper.readTree(bodies.get(0)).path("response_format").path("type").asText());
            assertEquals("json_object", mapper.readTree(bodies.get(1)).path("response_format").path("type").asText());
            assertTrue(bodies.get(0).contains("\"strict\":true"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesARateLimitThenReadsTheDocument() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(429, 200), List.of(
                "{\"error\":{\"message\":\"rate\"}}",
                "{\"model\":\"ok\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}"
        ));
        try {
            OpenRouterClient.Completion completion = client(server, 2).complete("model", "system", "user", null, "none");
            assertEquals(true, completion.content().path("ok").asBoolean());
            assertEquals(2, bodies.size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void discardsAPaidResultAndTriesTheFreeFallback() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(200, 200), List.of(
                "{\"model\":\"openai/gpt-4o\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"cost\":0.02}}",
                "{\"model\":\"meta/llama:free\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"cost\":0}}"
        ));
        try {
            OpenRouterClient.Route route = new OpenRouterClient.Route("openrouter/free", List.of("meta/llama:free"), false, 0, 2, AttemptBudget.unlimited());
            OpenRouterClient.Completion completion = client(server, 0).complete(route, "system", "user", null, "none");
            assertEquals("meta/llama:free", completion.model());
            assertEquals("meta/llama:free", completion.requestedModel());
            assertEquals("openrouter/free", mapper.readTree(bodies.get(0)).path("model").asText());
            assertEquals("meta/llama:free", mapper.readTree(bodies.get(1)).path("model").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotCallAPaidFallbackWhenThatSwitchIsDisabled() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(500), List.of("{\"error\":{\"message\":\"down\"}}"));
        try {
            OpenRouterClient.Route route = new OpenRouterClient.Route("openrouter/free", List.of("openai/gpt-4o:floor"), false, 0, 2, AttemptBudget.unlimited());
            OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client(server, 0).complete(route, "system", "user", null, "none"));
            assertEquals(OpenRouterException.Kind.PROVIDER, exception.kind());
            assertEquals(1, bodies.size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesAFloorModelOnlyWhenPaidFallbackIsEnabled() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(500, 200), List.of(
                "{\"error\":{\"message\":\"down\"}}",
                "{\"model\":\"openai/gpt-4o\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"cost\":0.01}}"
        ));
        try {
            OpenRouterClient.Route route = new OpenRouterClient.Route("openrouter/free", List.of("openai/gpt-4o:floor"), true, 0, 2, AttemptBudget.unlimited());
            OpenRouterClient.Completion completion = client(server, 0).complete(route, "system", "user", null, "none");
            assertEquals("openai/gpt-4o", completion.model());
            assertEquals("openai/gpt-4o:floor", mapper.readTree(bodies.get(1)).path("model").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesWhenReasoningConsumesTheOutputBudget() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(200, 200), List.of(
                "{\"model\":\"nvidia/nemotron:free\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":8000}}",
                "{\"model\":\"nvidia/nemotron:free\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":20}}"
        ));
        try {
            OpenRouterClient.Completion completion = client(server, 0).complete("openrouter/free", "system", "user", null, "none");
            assertEquals(true, completion.content().path("ok").asBoolean());
            assertEquals(1024, mapper.readTree(bodies.get(0)).path("reasoning").path("max_tokens").asInt());
            assertEquals(16000, mapper.readTree(bodies.get(1)).path("max_tokens").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipsAProviderThatSpendsTheBudgetOnReasoning() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(200, 200), List.of(
                "{\"provider\":\"AtlasCloud\",\"model\":\"dots-studio/dots-3-note-preview:free\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":8000,\"completion_tokens_details\":{\"reasoning_tokens\":6976}}}",
                "{\"provider\":\"Nvidia\",\"model\":\"nvidia/nemotron:free\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":20}}"
        ));
        server.createContext("/v1/providers", exchange -> {
            byte[] payload = "{\"data\":[{\"name\":\"AtlasCloud\",\"slug\":\"atlas-cloud\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        try {
            OpenRouterClient.Completion completion = client(server, 0).complete("openrouter/free", "system", "user", null, "none");
            assertEquals(true, completion.content().path("ok").asBoolean());
            assertEquals(8000, mapper.readTree(bodies.get(0)).path("max_tokens").asInt());
            assertEquals(8000, mapper.readTree(bodies.get(1)).path("max_tokens").asInt());
            JsonNode retry = mapper.readTree(bodies.get(1));
            assertEquals("none", retry.path("reasoning").path("effort").asText());
            assertTrue(retry.path("reasoning").path("max_tokens").isMissingNode());
            assertEquals("atlas-cloud", retry.path("provider").path("ignore").path(0).asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void restoresReasoningWhenTheEndpointRequiresIt() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(200, 400, 200), List.of(
                "{\"provider\":\"AtlasCloud\",\"model\":\"dots-studio/dots-3-note-preview:free\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}],\"usage\":{\"completion_tokens_details\":{\"reasoning_tokens\":6976}}}",
                "{\"error\":{\"message\":\"Reasoning is mandatory for this endpoint and cannot be disabled.\",\"code\":400}}",
                "{\"model\":\"nvidia/nemotron:free\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"
        ));
        server.createContext("/v1/providers", exchange -> {
            byte[] payload = "{\"data\":[{\"name\":\"AtlasCloud\",\"slug\":\"atlas-cloud\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        try {
            OpenRouterClient.Completion completion = client(server, 2).complete("openrouter/free", "system", "user", null, "none");
            assertEquals(true, completion.content().path("ok").asBoolean());
            assertEquals("none", mapper.readTree(bodies.get(1)).path("reasoning").path("effort").asText());
            JsonNode restored = mapper.readTree(bodies.get(2));
            assertEquals(1024, restored.path("reasoning").path("max_tokens").asInt());
            assertTrue(restored.path("reasoning").path("effort").isMissingNode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsAReasoningLockInsteadOfAnEmptyPage() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(200), List.of(
                "{\"id\":\"gen-1\",\"error\":{\"message\":\"Reasoning is mandatory for this endpoint and cannot be disabled.\",\"code\":400}}"
        ));
        try {
            OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client(server, 2).complete("deepseek/deepseek-v4.1-flash", "system", "user", null, "none"));
            assertEquals(OpenRouterException.Kind.PROVIDER, exception.kind());
            assertEquals("Reasoning is mandatory for this endpoint and cannot be disabled.", exception.getMessage());
            assertEquals(1, bodies.size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsAnObjectWrappedInProseOrTextParts() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer fenced = server(bodies, List.of(200), List.of(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Here is the page:\\n```json\\n{\\\"title\\\":\\\"Ada\\\"}\\n```\"}}]}"
        ));
        try {
            OpenRouterClient.Completion completion = client(fenced, 0).complete("model", "system", "user", null, "none");
            assertEquals("Ada", completion.content().path("title").asText());
        } finally {
            fenced.stop(0);
        }
        HttpServer parts = server(new ArrayList<>(), List.of(200), List.of(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"ok\\\":true}\"}]}}]}"
        ));
        try {
            OpenRouterClient.Completion completion = client(parts, 0).complete("model", "system", "user", null, "none");
            assertEquals(true, completion.content().path("ok").asBoolean());
        } finally {
            parts.stop(0);
        }
    }

    @Test
    void retriesWhenACutOffObjectUsesTheOutputBudget() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = server(bodies, List.of(200, 200), List.of(
                "{\"model\":\"nvidia/nemotron:free\",\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"documentId\\\":\\\"x\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":8000}}",
                "{\"model\":\"nvidia/nemotron:free\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":20}}"
        ));
        try {
            OpenRouterClient.Completion completion = client(server, 0).complete("openrouter/free", "system", "user", null, "none");
            assertEquals(true, completion.content().path("ok").asBoolean());
            assertEquals(8000, mapper.readTree(bodies.get(0)).path("max_tokens").asInt());
            assertEquals(16000, mapper.readTree(bodies.get(1)).path("max_tokens").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAnEmptyOrMalformedBody() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer empty = server(bodies, List.of(200), List.of("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        try {
            OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client(empty, 0).complete("model", "system", "user", null, "none"));
            assertEquals(OpenRouterException.Kind.EMPTY, exception.kind());
        } finally {
            empty.stop(0);
        }
        HttpServer malformed = server(new ArrayList<>(), List.of(200), List.of("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"));
        try {
            OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client(malformed, 0).complete("model", "system", "user", null, "none"));
            assertEquals(OpenRouterException.Kind.MALFORMED, exception.kind());
        } finally {
            malformed.stop(0);
        }
        HttpServer array = server(new ArrayList<>(), List.of(200), List.of("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"[{\\\"ok\\\":true}]\"}}]}"));
        try {
            OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client(array, 0).complete("model", "system", "user", null, "none"));
            assertEquals(OpenRouterException.Kind.MALFORMED, exception.kind());
        } finally {
            array.stop(0);
        }
    }

    private HttpOpenRouterClient client(HttpServer server, int retries) {
        PrismProperties properties = new PrismProperties();
        properties.getOpenrouter().setApiKey("test-key");
        properties.getOpenrouter().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.getOpenrouter().setTimeoutSeconds(5);
        properties.getOpenrouter().setMaxRetries(retries);
        properties.setPublicBaseUrl("http://localhost");
        return new HttpOpenRouterClient(properties, mapper);
    }

    private HttpServer server(List<String> bodies, List<Integer> statuses, List<String> payloads) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger index = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int current = Math.min(index.getAndIncrement(), statuses.size() - 1);
            byte[] body = payloads.get(current).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses.get(current), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}

package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class OpenRouterMessages {

    private OpenRouterMessages() {
    }

    public static ObjectNode body(ObjectMapper mapper, String model, String system, String user, JsonNode schema, String schemaName, int maxTokens, boolean structured) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("max_tokens", Math.max(256, maxTokens));
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        body.putObject("reasoning").put("max_tokens", 1024);
        if (structured && schema != null) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode json = format.putObject("json_schema");
            json.put("name", schemaName);
            json.put("strict", true);
            json.set("schema", schema);
            body.putObject("provider").put("require_parameters", true);
        } else {
            body.putObject("response_format").put("type", "json_object");
        }
        return body;
    }
}

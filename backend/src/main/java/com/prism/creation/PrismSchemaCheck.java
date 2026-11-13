package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public final class PrismSchemaCheck {

    private PrismSchemaCheck() {
    }

    public static String problem(JsonNode schema, JsonNode instance) {
        try {
            check(schema, instance, schema, "$");
            return null;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    private static void check(JsonNode schema, JsonNode instance, JsonNode root, String path) {
        if (schema == null || schema.isMissingNode()) {
            throw problem(path);
        }
        if (schema.has("$ref")) {
            check(resolve(root, schema.get("$ref").asText()), instance, root, path);
            return;
        }
        if (schema.has("const") && !same(schema.get("const"), instance)) {
            throw problem(path);
        }
        if (schema.has("enum") && !matchesEnum(schema.get("enum"), instance)) {
            throw problem(path);
        }
        switch (schema.path("type").asText("")) {
            case "object" -> object(schema, instance, root, path);
            case "array" -> array(schema, instance, root, path);
            case "string" -> {
                if (instance == null || !instance.isTextual()) {
                    throw problem(path);
                }
            }
            case "integer" -> {
                if (instance == null || !instance.isIntegralNumber()) {
                    throw problem(path);
                }
            }
            case "number" -> {
                if (instance == null || !instance.isNumber()) {
                    throw problem(path);
                }
            }
            default -> {
            }
        }
    }

    private static void object(JsonNode schema, JsonNode instance, JsonNode root, String path) {
        if (instance == null || !instance.isObject()) {
            throw problem(path);
        }
        if (schema.has("required")) {
            for (JsonNode name : schema.get("required")) {
                JsonNode value = instance.get(name.asText());
                if (value == null || value.isNull()) {
                    throw new IllegalArgumentException("The response does not match the WebAux schema. Missing " + name.asText() + ".");
                }
            }
        }
        JsonNode properties = schema.path("properties");
        boolean closed = schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean(true);
        instance.fieldNames().forEachRemaining(field -> {
            if (properties.has(field)) {
                check(properties.get(field), instance.get(field), root, path + "." + field);
            } else if (closed) {
                throw new IllegalArgumentException("The response does not match the WebAux schema. Unexpected " + field + ".");
            }
        });
    }

    private static void array(JsonNode schema, JsonNode instance, JsonNode root, String path) {
        if (instance == null || !instance.isArray()) {
            throw problem(path);
        }
        JsonNode items = schema.get("items");
        if (items == null) {
            return;
        }
        int index = 0;
        for (JsonNode item : instance) {
            check(items, item, root, path + "[" + index + "]");
            index++;
        }
    }

    private static JsonNode resolve(JsonNode root, String ref) {
        if (ref == null || !ref.startsWith("#/")) {
            throw new IllegalArgumentException("The response does not match the WebAux schema.");
        }
        JsonNode node = root;
        for (String part : ref.substring(2).split("/")) {
            node = node.path(part);
        }
        if (node.isMissingNode()) {
            throw new IllegalArgumentException("The response does not match the WebAux schema.");
        }
        return node;
    }

    private static boolean matchesEnum(JsonNode options, JsonNode instance) {
        for (JsonNode option : options) {
            if (same(option, instance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean same(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null || actual.isNull()) {
            return false;
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        return expected.equals(actual);
    }

    private static IllegalArgumentException problem(String path) {
        return new IllegalArgumentException("The response does not match the WebAux schema at " + path + ".");
    }

    static BigDecimal money(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }
}

package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static boolean visible(JsonNode when, JsonNode answers) {
        if (when == null || when.isNull() || when.isMissingNode() || when.isEmpty()) {
            return true;
        }
        if (when.has("all")) {
            for (JsonNode rule : when.get("all")) {
                if (!visible(rule, answers)) {
                    return false;
                }
            }
            return true;
        }
        if (when.has("any")) {
            for (JsonNode rule : when.get("any")) {
                if (visible(rule, answers)) {
                    return true;
                }
            }
            return when.get("any").isEmpty();
        }
        String key = when.path("key").asText("");
        JsonNode actual = answers == null ? null : answers.get(key);
        if (when.has("eq")) {
            return same(actual, when.get("eq"));
        }
        if (when.has("neq")) {
            return !same(actual, when.get("neq"));
        }
        if (when.has("contains")) {
            return contains(actual, when.get("contains"));
        }
        if (when.has("empty")) {
            return when.get("empty").asBoolean() == isEmpty(actual);
        }
        if (when.has("gt")) {
            return number(actual) > when.get("gt").asDouble();
        }
        if (when.has("lt")) {
            return number(actual) < when.get("lt").asDouble();
        }
        return true;
    }

    private static boolean same(JsonNode actual, JsonNode expected) {
        if (isEmpty(actual) && (expected == null || expected.isNull())) {
            return true;
        }
        if (actual == null || actual.isNull() || expected == null || expected.isNull()) {
            return false;
        }
        if (actual.isNumber() && expected.isNumber()) {
            return actual.asDouble() == expected.asDouble();
        }
        return actual.asText("").trim().toLowerCase(Locale.ROOT).equals(expected.asText("").trim().toLowerCase(Locale.ROOT));
    }

    private static boolean contains(JsonNode actual, JsonNode expected) {
        if (actual == null || actual.isNull()) {
            return false;
        }
        String needle = expected.asText("").toLowerCase(Locale.ROOT);
        if (actual.isArray()) {
            for (JsonNode item : actual) {
                if (item.asText("").toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
            return false;
        }
        return actual.asText("").toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean isEmpty(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return true;
        }
        if (value.isArray()) {
            return value.isEmpty();
        }
        return value.asText("").isBlank();
    }

    private static double number(JsonNode value) {
        if (value == null || value.isNull()) {
            return 0;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText("0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}

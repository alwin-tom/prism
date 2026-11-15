package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.exception.ApiException;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class QuestionnaireCatalog {

    private static final Pattern UUID_TEXT = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final int MAX_ANSWER_CHARS = 200_000;
    private static final int MAX_TEXT = 4000;
    private static final int MAX_ASSETS = 24;

    private QuestionnaireCatalog() {
    }

    public static ObjectNode keepAnswers(ObjectMapper mapper, JsonNode definition, JsonNode incoming) {
        if (incoming == null || !incoming.isObject()) {
            throw ApiException.badRequest("Answers must be an object.");
        }
        if (incoming.toString().length() > MAX_ANSWER_CHARS) {
            throw ApiException.badRequest("Those answers are too large.");
        }
        ObjectNode kept = mapper.createObjectNode();
        for (JsonNode question : definition.path("questions")) {
            String key = question.path("key").asText("");
            if (key.isBlank() || !incoming.has(key)) {
                continue;
            }
            kept.set(key, value(mapper, question, incoming.get(key)));
        }
        copyUserPalette(mapper, kept, incoming);
        return kept;
    }

    public static ObjectNode visibleAnswers(ObjectMapper mapper, JsonNode definition, JsonNode answers) {
        ObjectNode visible = mapper.createObjectNode();
        for (JsonNode question : definition.path("questions")) {
            String key = question.path("key").asText("");
            if (key.isBlank() || !ConditionEvaluator.visible(question.get("when"), answers)) {
                continue;
            }
            if (answers != null && answers.has(key)) {
                visible.set(key, answers.get(key));
            }
        }
        copyUserPalette(mapper, visible, answers);
        return visible;
    }

    /** DraftSide palette is not a questionnaire field but must reach generation. */
    public static void copyUserPalette(ObjectMapper mapper, ObjectNode target, JsonNode source) {
        if (target == null || source == null || !source.isObject()) {
            return;
        }
        JsonNode palette = source.path("colourPalette");
        if (!palette.isObject()) {
            palette = source.path("colorPalette");
        }
        if (!palette.isObject()) {
            return;
        }
        String background = palette.path("background").asText("").trim();
        String ink = palette.path("ink").asText("").trim();
        String accent = palette.path("accent").asText("").trim();
        if (!isHex(background) || !isHex(ink) || !isHex(accent)) {
            return;
        }
        ObjectNode kept = mapper.createObjectNode();
        kept.put("background", background);
        kept.put("ink", ink);
        kept.put("accent", accent);
        String name = palette.path("name").asText("").trim();
        if (!name.isBlank() && name.length() <= 64) {
            kept.put("name", name);
        }
        target.set("colourPalette", kept);
    }

    private static boolean isHex(String value) {
        return value != null && value.matches("(?i)^#([0-9a-f]{3}|[0-9a-f]{6})$");
    }

    public static void requireVisible(JsonNode definition, JsonNode answers) {
        for (JsonNode question : definition.path("questions")) {
            if (!question.path("required").asBoolean(false) || !ConditionEvaluator.visible(question.get("when"), answers)) {
                continue;
            }
            if (empty(answers == null ? null : answers.get(question.path("key").asText()))) {
                throw ApiException.badRequest("Answer the required question: " + question.path("label").asText("a required question") + ".");
            }
        }
    }

    public static Set<String> mediaIds(JsonNode definition, JsonNode answers) {
        Set<String> ids = new HashSet<>();
        for (JsonNode question : definition.path("questions")) {
            if (!"images".equals(question.path("type").asText()) || !ConditionEvaluator.visible(question.get("when"), answers)) {
                continue;
            }
            JsonNode value = answers == null ? null : answers.get(question.path("key").asText());
            if (value == null || !value.isArray()) {
                continue;
            }
            for (JsonNode item : value) {
                String id = item.asText("").toLowerCase(Locale.ROOT);
                if (UUID_TEXT.matcher(id).matches()) {
                    ids.add(id);
                }
                if (ids.size() > MAX_ASSETS) {
                    throw ApiException.badRequest("Upload at most 24 files for this website.");
                }
            }
        }
        return ids;
    }

    private static JsonNode value(ObjectMapper mapper, JsonNode question, JsonNode raw) {
        String type = question.path("type").asText("text");
        if ("images".equals(type)) {
            ArrayNode files = mapper.createArrayNode();
            if (raw != null && raw.isArray()) {
                for (JsonNode item : raw) {
                    String id = item.asText("");
                    if (UUID_TEXT.matcher(id).matches()) {
                        files.add(id.toLowerCase(Locale.ROOT));
                    }
                    if (files.size() > MAX_ASSETS) {
                        throw ApiException.badRequest("Upload at most 24 files for this website.");
                    }
                }
            }
            return files;
        }
        if ("repeat".equals(type)) {
            ArrayNode rows = mapper.createArrayNode();
            if (raw != null && raw.isArray()) {
                int count = 0;
                for (JsonNode row : raw) {
                    if (++count > 12 || !row.isObject()) {
                        continue;
                    }
                    ObjectNode kept = mapper.createObjectNode();
                    for (JsonNode field : question.path("item")) {
                        String key = field.path("key").asText("");
                        if (!key.isBlank() && row.has(key)) {
                            kept.put(key, clip(row.get(key).asText("")));
                        }
                    }
                    rows.add(kept);
                }
            }
            return rows;
        }
        if ("number".equals(type) && raw != null && raw.isNumber()) {
            return raw;
        }
        if (raw != null && raw.isArray()) {
            ArrayNode items = mapper.createArrayNode();
            for (JsonNode item : raw) {
                items.add(clip(item.asText("")));
                if (items.size() > 20) {
                    break;
                }
            }
            return items;
        }
        return mapper.getNodeFactory().textNode(clip(raw == null ? "" : raw.asText("")));
    }

    private static String clip(String value) {
        String text = value == null ? "" : value;
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    private static boolean empty(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isArray()) {
            return value.isEmpty();
        }
        return value.asText("").isBlank();
    }
}

package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Fills empty COUNTDOWN targets from questionnaire dates so generated pages show a live clock.
 */
public final class CountdownEnricher {

    private static final String[] DATE_KEYS = {
            "weddingDate", "partyDate", "eventDate", "ceremonyDate", "date", "startDate"
    };
    private static final String[] TIME_KEYS = {
            "weddingTime", "partyTime", "eventTime", "ceremonyTime", "time", "startTime"
    };

    private CountdownEnricher() {
    }

    public static ObjectNode apply(ObjectNode document, JsonNode answers) {
        if (document == null) {
            return null;
        }
        String target = isoFromAnswers(answers);
        if (target == null || target.isBlank()) {
            return document;
        }
        ObjectNode copy = document.deepCopy();
        if (copy.path("root") instanceof ObjectNode root) {
            fill(root, target);
        }
        return copy;
    }

    public static String isoFromAnswers(JsonNode answers) {
        if (answers == null || !answers.isObject()) {
            return null;
        }
        String date = firstText(answers, DATE_KEYS);
        if (date == null || date.isBlank()) {
            return null;
        }
        String time = firstText(answers, TIME_KEYS);
        return combine(date.trim(), time == null ? "" : time.trim());
    }

    private static void fill(ObjectNode node, String target) {
        if ("COUNTDOWN".equals(node.path("type").asText(""))) {
            ObjectNode props = node.with("props");
            if (props.path("target").asText("").isBlank()) {
                props.put("target", target);
            }
            if (props.path("label").asText("").isBlank()) {
                props.put("label", "Until the day");
            }
        }
        for (int i = 0; i < node.path("children").size(); i++) {
            if (node.path("children").get(i) instanceof ObjectNode child) {
                fill(child, target);
            }
        }
    }

    private static String firstText(JsonNode answers, String[] keys) {
        for (String key : keys) {
            String value = answers.path(key).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String combine(String date, String time) {
        LocalDate day = parseDate(date);
        if (day == null) {
            return null;
        }
        LocalTime clock = parseTime(time);
        LocalDateTime when = LocalDateTime.of(day, clock == null ? LocalTime.NOON : clock);
        return when.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }
}

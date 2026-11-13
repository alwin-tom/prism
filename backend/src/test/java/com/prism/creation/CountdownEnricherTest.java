package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountdownEnricherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fillsEmptyCountdownFromWeddingDate() {
        ObjectNode answers = mapper.createObjectNode();
        answers.put("weddingDate", "2027-10-10");
        answers.put("weddingTime", "11:11");
        ObjectNode document = mapper.createObjectNode();
        ObjectNode root = document.putObject("root");
        root.put("type", "PAGE");
        ObjectNode countdown = root.putArray("children").addObject();
        countdown.put("type", "COUNTDOWN");
        countdown.putObject("props");

        ObjectNode enriched = CountdownEnricher.apply(document, answers);
        assertEquals("2027-10-10T11:11:00", enriched.path("root").path("children").get(0).path("props").path("target").asText());
        assertEquals("Until the day", enriched.path("root").path("children").get(0).path("props").path("label").asText());
    }

    @Test
    void leavesExistingTargetAlone() {
        ObjectNode answers = mapper.createObjectNode().put("eventDate", "2028-01-01");
        ObjectNode document = mapper.createObjectNode();
        ObjectNode root = document.putObject("root");
        root.put("type", "PAGE");
        ObjectNode countdown = root.putArray("children").addObject();
        countdown.put("type", "COUNTDOWN");
        countdown.putObject("props").put("target", "2029-05-05T12:00:00");

        ObjectNode enriched = CountdownEnricher.apply(document, answers);
        assertEquals("2029-05-05T12:00:00", enriched.path("root").path("children").get(0).path("props").path("target").asText());
    }

    @Test
    void parsesIsoFromAnswers() {
        ObjectNode answers = mapper.createObjectNode().put("partyDate", "2026-12-31");
        assertTrue(CountdownEnricher.isoFromAnswers(answers).startsWith("2026-12-31T"));
    }
}

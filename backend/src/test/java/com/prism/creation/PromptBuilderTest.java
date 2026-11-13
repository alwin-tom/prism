package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void formalStyleTellsTheModelToWriteProgrammeProse() {
        ObjectNode answers = mapper.createObjectNode().put("style", "formal").put("objective", "Inform the stakeholders");
        String plan = PromptBuilder.plan("CORPORATE", answers, Set.of());
        String document = PromptBuilder.document("CORPORATE", answers, Set.of(), mapper.createObjectNode().put("voice", "formal"));
        assertTrue(plan.contains("Selected tone: formal"));
        assertTrue(plan.contains("programme note"));
        assertTrue(document.contains("two to four sentences"));
        assertTrue(document.contains("Selected tone: formal"));
        assertFalse(document.contains("Date:"));
    }

    @Test
    void birthdayThemeIsTheToneAndConferenceThemeIsTheSubject() {
        ObjectNode birthday = mapper.createObjectNode().put("theme", "playful");
        String birthdayPlan = PromptBuilder.plan("BIRTHDAY", birthday, Set.of());
        assertTrue(birthdayPlan.contains("Selected tone: playful"));

        ObjectNode conference = mapper.createObjectNode().put("theme", "Coastal cities");
        String conferencePlan = PromptBuilder.plan("CONFERENCE", conference, Set.of());
        assertTrue(conferencePlan.contains("No tone was selected"));
        assertTrue(conferencePlan.contains("formal"));
        assertTrue(conferencePlan.contains("Coastal cities"));
        assertFalse(conferencePlan.contains("Selected tone: Coastal cities"));
    }
}

package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evaluatesComparisonsAndGroups() throws Exception {
        ObjectNode answers = mapper.createObjectNode();
        answers.put("countdown", "yes");
        answers.put("guests", 40);
        answers.putArray("themes").add("floral");
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"countdown\",\"eq\":\"yes\"}"), answers));
        assertFalse(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"countdown\",\"neq\":\"yes\"}"), answers));
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"themes\",\"contains\":\"floral\"}"), answers));
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"missing\",\"empty\":true}"), answers));
        assertFalse(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"countdown\",\"empty\":true}"), answers));
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"guests\",\"gt\":10}"), answers));
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"key\":\"guests\",\"lt\":80}"), answers));
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"all\":[{\"key\":\"countdown\",\"eq\":\"yes\"},{\"key\":\"guests\",\"gt\":10}]}"), answers));
        assertFalse(ConditionEvaluator.visible(mapper.readTree("{\"all\":[{\"key\":\"countdown\",\"eq\":\"no\"},{\"key\":\"guests\",\"gt\":10}]}"), answers));
        assertTrue(ConditionEvaluator.visible(mapper.readTree("{\"any\":[{\"key\":\"countdown\",\"eq\":\"no\"},{\"key\":\"guests\",\"gt\":10}]}"), answers));
        assertTrue(ConditionEvaluator.visible(null, answers));
    }
}

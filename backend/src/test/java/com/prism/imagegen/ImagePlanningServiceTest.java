package com.prism.imagegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.shared.config.PrismProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePlanningServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PrismProperties properties = new PrismProperties();
    private final ImagePlanningService planning = new ImagePlanningService(properties, new FallbackImagePlanner());

    @Test
    void validatesAndCapsPlan() throws Exception {
        properties.getImage().setEnabled(true);
        properties.getImage().setMaxImagesPerWebsite(2);
        ObjectNode root = mapper.createObjectNode();
        ArrayNode plan = root.putArray("imagePlan");
        plan.add(item("hero-main", "A cinematic editorial photograph of soft morning light, no text."));
        plan.add(item("gallery-1", "A quiet documentary photograph outdoors, no text."));
        plan.add(item("gallery-2", "Another quiet documentary photograph outdoors, no text."));
        List<ImagePlanItem> items = planning.plan(root, "wedding", mapper.createObjectNode(), Set.of());
        assertEquals(2, items.size());
        assertEquals("hero-main", items.getFirst().assetKey());
    }

    @Test
    void rejectsTextHeavyAndDuplicates() throws Exception {
        properties.getImage().setEnabled(true);
        ObjectNode root = mapper.createObjectNode();
        ArrayNode plan = root.putArray("imagePlan");
        plan.add(item("bad", "Create a poster with the words Alwin and Preethi and the date."));
        plan.add(item("good", "Soft botanical photograph with gentle daylight, no text."));
        plan.add(item("dup", "Soft botanical photograph with gentle daylight, no text."));
        List<ImagePlanItem> items = planning.plan(root, "wedding", mapper.createObjectNode(), Set.of());
        assertEquals(1, items.size());
        assertEquals("good", items.getFirst().assetKey());
    }

    @Test
    void usesFallbackWhenMissing() {
        properties.getImage().setEnabled(true);
        List<ImagePlanItem> items = planning.plan(null, "wedding", mapper.createObjectNode(), Set.of());
        assertFalse(items.isEmpty());
        assertTrue(items.size() <= 3);
    }

    private ObjectNode item(String key, String prompt) {
        ObjectNode node = mapper.createObjectNode();
        node.put("assetKey", key);
        node.put("type", "HERO");
        node.put("sectionId", "hero");
        node.put("purpose", "Hero");
        node.put("prompt", prompt);
        node.put("aspectRatio", "16:9");
        node.put("priority", "HIGH");
        node.put("required", true);
        return node;
    }
}

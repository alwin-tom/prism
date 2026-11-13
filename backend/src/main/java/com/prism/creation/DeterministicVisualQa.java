package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Structure-based visual QA that always runs. Vision/screenshot evaluation is recorded as unavailable
 * unless a separate vision stage is configured later.
 */
public final class DeterministicVisualQa {

    private DeterministicVisualQa() {
    }

    public static ObjectNode assess(ObjectMapper mapper, JsonNode document, JsonNode blueprint, String archetypeId) {
        List<Finding> findings = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        checks.add("structure");
        checks.add("section-rhythm");
        checks.add("copy-presence");
        checks.add("media-ownership-shape");
        checks.add("responsive-stubs");
        checks.add("vision-unavailable");

        List<JsonNode> sections = new ArrayList<>();
        collectSections(document == null ? null : document.path("root"), sections);
        if (sections.isEmpty()) {
            findings.add(Finding.critical("composition", "The page has no sections.", "root", "Add a hero SECTION and at least one content SECTION."));
        }
        if (sections.size() == 1) {
            findings.add(Finding.major("hierarchy", "Only a hero exists.", "root", "Add supporting sections from the blueprint."));
        }

        Set<String> backgrounds = new LinkedHashSet<>();
        Set<String> alignments = new LinkedHashSet<>();
        int emptyCopy = 0;
        int textNodes = 0;
        int images = 0;
        int carousels = 0;
        for (JsonNode section : sections) {
            String background = section.path("styles").path("background").asText("");
            if (!background.isBlank()) {
                backgrounds.add(background);
            }
            alignments.add(section.path("styles").path("textAlign").asText("center"));
            int[] counts = new int[]{0, 0, 0, 0};
            tally(section, counts);
            emptyCopy += counts[0];
            textNodes += counts[1];
            images += counts[2];
            carousels += counts[3];
        }
        if (sections.size() >= 3 && backgrounds.size() < 2) {
            findings.add(Finding.major("composition", "Consecutive sections share one background.", "sections", "Alternate page and surface backgrounds."));
        }
        if (sections.size() >= 3 && alignments.size() == 1 && "center".equals(alignments.iterator().next())) {
            findings.add(Finding.minor("composition", "Every section is centre-aligned.", "sections", "Use a split or left-aligned section for rhythm."));
        }
        if (textNodes > 0 && emptyCopy > 0) {
            findings.add(Finding.critical("content", "Some headings or paragraphs are empty.", "copy", "Fill props.text from supplied answers only."));
        }
        if (blueprint != null && blueprint.path("sections").isArray()) {
            int planned = blueprint.path("sections").size() + 1;
            if (sections.size() + 1 < Math.min(planned, 3)) {
                findings.add(Finding.major("composition", "The document has fewer sections than the blueprint.", "blueprint", "Materialise each planned section that the answers support."));
            }
            Set<String> compositions = new LinkedHashSet<>();
            for (JsonNode section : blueprint.path("sections")) {
                compositions.add(section.path("composition").asText(""));
            }
            if (compositions.size() == 1 && blueprint.path("sections").size() > 2) {
                findings.add(Finding.major("composition", "Blueprint repeats one composition.", "blueprint", "Vary adjacent section compositions."));
            }
        }
        if (images >= 2 && carousels == 0) {
            findings.add(Finding.minor("imagery", "Multiple images are present without a carousel.", "media", "Group two or more images in one CAROUSEL when they share a section."));
        }
        if (archetypeId != null && !archetypeId.isBlank() && document != null) {
            String stored = document.path("designTokens").path("meta").path("archetypeId").asText("");
            if (!stored.isBlank() && !stored.equalsIgnoreCase(archetypeId)) {
                findings.add(Finding.info("alignment", "Document archetype metadata differs from the selected direction.", "designTokens.meta", "Keep meta.archetypeId aligned with the creative direction."));
            }
        }
        findings.add(Finding.info("motion", "Vision screenshot evaluation did not run.", "pipeline", "Deterministic structure checks only."));

        String status = "pass";
        for (Finding finding : findings) {
            if ("critical".equals(finding.severity) || "major".equals(finding.severity)) {
                status = "improve";
                break;
            }
        }

        ObjectNode report = mapper.createObjectNode();
        ArrayNode checksNode = report.putArray("checksRun");
        checks.forEach(checksNode::add);
        report.put("overallStatus", status);
        ArrayNode found = report.putArray("findings");
        for (Finding finding : findings) {
            ObjectNode item = found.addObject();
            item.put("severity", finding.severity);
            item.put("dimension", finding.dimension);
            item.put("observation", finding.observation);
            item.put("region", finding.region);
            item.put("suggestedAction", finding.action);
        }
        return report;
    }

    public static List<String> actionable(JsonNode report) {
        List<String> notes = new ArrayList<>();
        if (report == null) {
            return notes;
        }
        for (JsonNode finding : report.path("findings")) {
            String severity = finding.path("severity").asText("");
            if ("critical".equals(severity) || "major".equals(severity)) {
                notes.add(finding.path("suggestedAction").asText(finding.path("observation").asText("")));
            }
        }
        return notes;
    }

    private static void collectSections(JsonNode node, List<JsonNode> sections) {
        if (node == null || !node.isObject()) {
            return;
        }
        if ("SECTION".equals(node.path("type").asText(""))) {
            sections.add(node);
        }
        for (JsonNode child : node.path("children")) {
            collectSections(child, sections);
        }
    }

    private static void tally(JsonNode node, int[] counts) {
        String type = node.path("type").asText("");
        if ("HEADING".equals(type) || "PARAGRAPH".equals(type) || "QUOTE".equals(type) || "BUTTON".equals(type)) {
            counts[1]++;
            if (node.path("props").path("text").asText("").isBlank()) {
                counts[0]++;
            }
        }
        if ("IMAGE".equals(type) && !node.path("props").path("mediaId").asText("").isBlank()) {
            counts[2]++;
        }
        if ("CAROUSEL".equals(type)) {
            counts[3]++;
        }
        for (JsonNode child : node.path("children")) {
            tally(child, counts);
        }
    }

    private record Finding(String severity, String dimension, String observation, String region, String action) {
        static Finding critical(String dimension, String observation, String region, String action) {
            return new Finding("critical", dimension, observation, region, action);
        }

        static Finding major(String dimension, String observation, String region, String action) {
            return new Finding("major", dimension, observation, region, action);
        }

        static Finding minor(String dimension, String observation, String region, String action) {
            return new Finding("minor", dimension, observation, region, action);
        }

        static Finding info(String dimension, String observation, String region, String action) {
            return new Finding("info", dimension, observation, region, action);
        }
    }
}

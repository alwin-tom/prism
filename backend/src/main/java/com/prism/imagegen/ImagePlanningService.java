package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.shared.config.PrismProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ImagePlanningService {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,78}$");
    private static final Set<String> TYPES = Set.of("HERO", "BACKGROUND", "GALLERY", "ILLUSTRATION", "OTHER");
    private static final Set<String> ASPECTS = Set.of(
            "1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3", "21:9", "4:5", "5:4"
    );

    private final PrismProperties properties;
    private final FallbackImagePlanner fallback;

    public ImagePlanningService(PrismProperties properties, FallbackImagePlanner fallback) {
        this.properties = properties;
        this.fallback = fallback;
    }

    public List<ImagePlanItem> plan(JsonNode rawPlan, String category, JsonNode answers, Set<String> existingMediaIds) {
        if (!properties.getImage().isEnabled()) {
            return List.of();
        }
        int max = Math.max(0, Math.min(3, properties.getImage().getMaxImagesPerWebsite()));
        if (max == 0) {
            return List.of();
        }
        if (existingMediaIds != null && existingMediaIds.size() >= 4) {
            return List.of();
        }
        List<ImagePlanItem> fromLlm = parse(rawPlan, max);
        if (!fromLlm.isEmpty()) {
            return fromLlm;
        }
        return fallback.plan(category, answers, max);
    }

    List<ImagePlanItem> parse(JsonNode rawPlan, int max) {
        if (rawPlan == null || rawPlan.isNull() || rawPlan.isMissingNode()) {
            return List.of();
        }
        JsonNode array = rawPlan;
        if (rawPlan.isObject()) {
            array = rawPlan.path("imagePlan");
            if (!array.isArray()) {
                array = rawPlan.path("images");
            }
        }
        if (!array.isArray()) {
            return List.of();
        }
        List<ImagePlanItem> items = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        Set<String> promptFingerprints = new LinkedHashSet<>();
        for (JsonNode node : array) {
            if (items.size() >= max) {
                break;
            }
            ImagePlanItem item = normalize(node);
            if (item == null) {
                continue;
            }
            if (!keys.add(item.assetKey())) {
                continue;
            }
            String fingerprint = item.prompt().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (!promptFingerprints.add(fingerprint)) {
                continue;
            }
            items.add(item);
        }
        return List.copyOf(items);
    }

    private ImagePlanItem normalize(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String key = slug(node.path("assetKey").asText(node.path("key").asText("")));
        if (key.isBlank() || !KEY.matcher(key).matches()) {
            return null;
        }
        String type = node.path("type").asText("OTHER").trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            type = "OTHER";
        }
        String prompt = node.path("prompt").asText("").trim();
        if (prompt.length() < 12 || prompt.length() > 1200) {
            return null;
        }
        if (looksTextHeavy(prompt)) {
            return null;
        }
        String aspect = node.path("aspectRatio").asText(node.path("aspect_ratio").asText("16:9")).trim();
        if (!ASPECTS.contains(aspect)) {
            aspect = "16:9";
        }
        String priority = node.path("priority").asText("MEDIUM").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(priority)) {
            priority = "MEDIUM";
        }
        String sectionId = cleanId(node.path("sectionId").asText(node.path("section").asText("")));
        String targetNodeId = cleanId(node.path("targetNodeId").asText(node.path("nodeId").asText("")));
        String purpose = node.path("purpose").asText("").trim();
        if (purpose.length() > 240) {
            purpose = purpose.substring(0, 240);
        }
        boolean required = node.path("required").asBoolean("HIGH".equals(priority));
        return new ImagePlanItem(key, type, sectionId, targetNodeId, purpose, prompt, aspect, priority, required);
    }

    private static boolean looksTextHeavy(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        return lower.contains("text saying")
                || lower.contains("with the words")
                || lower.contains("typography")
                || lower.contains("lettering")
                || lower.contains("write the date")
                || lower.contains("include the name");
    }

    private static String slug(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String cleanId(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}

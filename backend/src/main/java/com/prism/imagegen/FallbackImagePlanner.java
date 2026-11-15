package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class FallbackImagePlanner {

    public List<ImagePlanItem> plan(String category, JsonNode answers, int max) {
        if (max <= 0) {
            return List.of();
        }
        String code = category == null ? "custom" : category.toLowerCase(Locale.ROOT);
        String names = text(answers, "coupleNames", "brideName", "groomName", "businessName", "eventName", "fullName");
        String place = text(answers, "venue", "city", "location", "place");
        String tone = text(answers, "style", "mood", "theme", "aesthetic");
        List<ImagePlanItem> items = new ArrayList<>();
        if (code.contains("wedding") || code.contains("anniversary")) {
            items.add(item("hero-main", "HERO", "hero",
                    "Cinematic editorial photograph of a quiet wedding morning atmosphere"
                            + (place.isBlank() ? "" : " in " + place)
                            + ", soft natural light, elegant flowers, no text, no logos, no readable signage"
                            + (tone.isBlank() ? "" : ", " + tone + " mood") + ".",
                    "16:9", "HIGH"));
            if (max > 1) {
                items.add(item("gallery-moment", "GALLERY", "gallery",
                        "Intimate documentary photograph of two people holding hands outdoors, warm daylight, shallow depth of field, no faces required, no text overlays.",
                        "4:5", "MEDIUM"));
            }
        } else if (code.contains("birthday") || code.contains("baby")) {
            items.add(item("hero-main", "HERO", "hero",
                    "Joyful celebration atmosphere photograph with soft pastel decorations and warm window light, tasteful and modern, no text, no banners with words.",
                    "16:9", "HIGH"));
        } else if (code.contains("portfolio") || code.contains("business") || code.contains("corporate")) {
            items.add(item("hero-main", "HERO", "hero",
                    "Clean modern workspace photography with natural light and subtle brand-ready atmosphere"
                            + (names.isBlank() ? "" : " suitable for " + names)
                            + ", no readable text, no logos.",
                    "16:9", "HIGH"));
            if (max > 1) {
                items.add(item("background-story", "BACKGROUND", "about",
                        "Abstract soft gradient photographic background with calm texture and professional depth, no text.",
                        "16:9", "MEDIUM"));
            }
        } else {
            items.add(item("hero-main", "HERO", "hero",
                    "High-quality atmospheric photograph matching a personal event website, elegant composition, soft light, no text, no logos.",
                    "16:9", "HIGH"));
        }
        if (items.size() > max) {
            return List.copyOf(items.subList(0, max));
        }
        return List.copyOf(items);
    }

    private static ImagePlanItem item(String key, String type, String section, String prompt, String aspect, String priority) {
        return new ImagePlanItem(key, type, section, "", "", prompt, aspect, priority, "HIGH".equals(priority));
    }

    private static String text(JsonNode answers, String... keys) {
        if (answers == null || answers.isNull()) {
            return "";
        }
        for (String key : keys) {
            JsonNode node = answers.path(key);
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
            if (node.isArray() && !node.isEmpty() && node.get(0).isTextual()) {
                return node.get(0).asText().trim();
            }
        }
        return "";
    }
}

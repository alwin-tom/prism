package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ArchetypeCatalog {

    private final JsonNode root;

    public ArchetypeCatalog(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource("creation/archetypes.json").getInputStream()) {
            this.root = mapper.readTree(input);
        } catch (Exception exception) {
            throw new IllegalStateException("The archetype catalogue could not be loaded.", exception);
        }
    }

    public String catalogueVersion() {
        return root.path("catalogueVersion").asText("1.0");
    }

    public List<JsonNode> all() {
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode item : root.path("archetypes")) {
            list.add(item);
        }
        return List.copyOf(list);
    }

    public Optional<JsonNode> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (JsonNode item : root.path("archetypes")) {
            if (key.equals(item.path("id").asText("").toLowerCase(Locale.ROOT))) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public List<JsonNode> forCategory(String category) {
        String code = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode item : root.path("archetypes")) {
            boolean match = false;
            for (JsonNode allowed : item.path("categories")) {
                if (code.equalsIgnoreCase(allowed.asText(""))) {
                    match = true;
                    break;
                }
            }
            if (match || code.isBlank()) {
                list.add(item);
            }
        }
        return list.isEmpty() ? all() : List.copyOf(list);
    }

    public String promptCatalog(String category) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : forCategory(category)) {
            text.append(item.path("id").asText()).append(" (").append(item.path("name").asText())
                    .append("; mood: ").append(join(item.path("mood")))
                    .append("; heroes: ").append(join(item.path("heroPatterns")))
                    .append("; avoid: ").append(join(item.path("avoid")))
                    .append("). ");
        }
        return text.toString().trim();
    }

    public String resolvePreference(String preference, String category, JsonNode answers) {
        if (preference != null && !preference.isBlank() && !"ai".equalsIgnoreCase(preference) && !"auto".equalsIgnoreCase(preference)) {
            Optional<JsonNode> chosen = byId(preference);
            if (chosen.isPresent() && compatible(chosen.get(), category)) {
                return chosen.get().path("id").asText();
            }
        }
        String style = answers == null ? "" : answers.path("style").asText("");
        if (style.isBlank() && answers != null) {
            style = answers.path("theme").asText("");
            if ("CONFERENCE".equalsIgnoreCase(category)) {
                style = "";
            }
        }
        String mapped = switch (style.toLowerCase(Locale.ROOT)) {
            case "luxury", "romantic", "elegant" -> "luxury-editorial";
            case "minimal" -> "modern-minimal";
            case "playful", "kids", "surprise" -> "playful-contemporary";
            case "soft", "floral" -> "botanical-romance";
            case "bold" -> "bold-typographic";
            case "editorial" -> "luxury-editorial";
            case "cultural", "traditional", "classic" -> "royal-heritage";
            case "rustic" -> "botanical-romance";
            case "formal" -> "classic-formal";
            default -> switch (category == null ? "" : category.toUpperCase(Locale.ROOT)) {
                case "WEDDING", "ANNIVERSARY" -> "luxury-editorial";
                case "BIRTHDAY" -> "playful-contemporary";
                case "BABY" -> "warm-storybook";
                case "PORTFOLIO" -> "contemporary-art";
                case "BUSINESS" -> "classic-formal";
                case "CONFERENCE", "CORPORATE" -> "modern-minimal";
                default -> "modern-minimal";
            };
        };
        return byId(mapped).filter(node -> compatible(node, category)).map(node -> node.path("id").asText()).orElseGet(() ->
                forCategory(category).stream().findFirst().map(node -> node.path("id").asText()).orElse("modern-minimal"));
    }

    public String directionId(String archetypeId) {
        return switch (archetypeId == null ? "" : archetypeId) {
            case "luxury-editorial" -> "modern-editorial";
            case "botanical-romance" -> "botanical-natural";
            case "modern-minimal" -> "elegant-minimal";
            case "royal-heritage" -> "vintage-classic";
            case "playful-contemporary" -> "playful-celebration";
            case "cinematic-luxury" -> "cinematic-dark";
            case "contemporary-art" -> "creative-portfolio";
            case "warm-storybook" -> "pastel-dream";
            case "bold-typographic" -> "bold-contemporary";
            case "classic-formal" -> "premium-business";
            default -> "modern-editorial";
        };
    }

    private boolean compatible(JsonNode archetype, String category) {
        if (category == null || category.isBlank()) {
            return true;
        }
        for (JsonNode allowed : archetype.path("categories")) {
            if (category.equalsIgnoreCase(allowed.asText(""))) {
                return true;
            }
        }
        return false;
    }

    private static String join(JsonNode array) {
        if (array == null || !array.isArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode item : array) {
            parts.add(item.asText(""));
        }
        return String.join(", ", parts);
    }
}

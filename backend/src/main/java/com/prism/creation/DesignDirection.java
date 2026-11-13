package com.prism.creation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

public record DesignDirection(
        String id,
        String name,
        String mood,
        String personality,
        String heroBackground,
        String pageBackground,
        String alternateBackground,
        String surface,
        String accent,
        String heading,
        String text,
        String muted,
        String lightText,
        String displayFont,
        String bodyFont,
        String radius,
        String shadow,
        String imageRadius,
        boolean heroDark,
        boolean pageDark,
        String composition
) {

    private static final List<DesignDirection> ALL = List.of(
            direction("luxury-romantic", "Luxury Romantic", "Elegant, intimate, warm", "Assured and sensory",
                    "linear-gradient(160deg, #4a1522, #8a3a45)", "#f6efe6", "#efe2d4", "#fffaf4", "#a68456",
                    "#4a1522", "#2b1a16", "#6e584c", "#f6efe6", "Fraunces", "Newsreader", "18px",
                    "0 18px 40px rgba(74,21,34,0.16)", "18px", true, false,
                    "Full-width hero, then a split story, then floating detail cards."),
            direction("modern-editorial", "Modern Editorial", "Specific, calm, magazine-like", "Considered and precise",
                    "linear-gradient(180deg, #1c1a17, #3a342c)", "#f7f4ef", "#ece7df", "#fffdf9", "#9a4b32",
                    "#1c1a17", "#2a2622", "#6b6258", "#f7f4ef", "Libre Franklin", "Newsreader", "2px",
                    "0 12px 28px rgba(28,26,23,0.10)", "2px", true, false,
                    "Centered editorial hero, then asymmetrical columns, then a quiet text band."),
            direction("cinematic-dark", "Cinematic Dark", "Nocturnal and cinematic", "Quiet luxury",
                    "linear-gradient(180deg, #0e1218, #243044)", "#12151c", "#1c2230", "#232a3a", "#d4b483",
                    "#f7f1e6", "#f3efe6", "#c8bfb0", "#f7f1e6", "Fraunces", "Public Sans", "16px",
                    "0 20px 48px rgba(0,0,0,0.28)", "16px", true, true,
                    "Immersive dark hero, then a lighter-on-dark split, then a card row."),
            direction("elegant-minimal", "Elegant Minimal", "Quiet and exact", "Restrained",
                    "#f3f1ec", "#f7f6f3", "#e7e4de", "#ffffff", "#1a1c1e",
                    "#1a1c1e", "#2a2c2e", "#6a6e72", "#f7f6f3", "Public Sans", "Source Serif 4", "0",
                    "none", "0", false, false,
                    "Wide centered hero with a thin rule, then stacked editorial sections."),
            direction("playful-celebration", "Playful Celebration", "Bright and hospitable", "Lively, still kind",
                    "linear-gradient(135deg, #e36b58, #f0a07a)", "#fff6ef", "#ffe8dc", "#ffffff", "#1f6f78",
                    "#2c2420", "#2c2420", "#6d5b52", "#fff6ef", "Libre Franklin", "Libre Franklin", "24px",
                    "0 14px 30px rgba(227,107,88,0.18)", "24px", false, false,
                    "Colour-led hero, then a playful card grid, then a carousel when photos exist."),
            direction("pastel-dream", "Pastel Dream", "Soft and gentle", "Airy",
                    "linear-gradient(160deg, #e7d5e4, #f8eef2)", "#f8eef2", "#f3e6ea", "#fffafb", "#6d5b8a",
                    "#3a3144", "#3a3144", "#7a7084", "#3a3144", "Newsreader", "Libre Franklin", "28px",
                    "0 16px 36px rgba(109,91,138,0.12)", "28px", false, false,
                    "Soft gradient hero, then rounded overlapping panels, then a pale detail band."),
            direction("modern-corporate", "Modern Corporate", "Clear and confident", "Professional",
                    "linear-gradient(160deg, #16324f, #1b6c80)", "#f4f7f8", "#e7eef1", "#ffffff", "#1b6c80",
                    "#16324f", "#1c2833", "#5c6b76", "#f4f7f8", "IBM Plex Sans", "IBM Plex Sans", "8px",
                    "0 10px 24px rgba(22,50,79,0.12)", "8px", true, false,
                    "Dark gradient hero, then a two-column agenda, then practical detail cards."),
            direction("premium-business", "Premium Business", "Polished and grounded", "Assured",
                    "linear-gradient(180deg, #1e3a32, #31584c)", "#f3efe6", "#e7e1d4", "#fffaf3", "#8c734b",
                    "#1e3a32", "#24312c", "#6a6256", "#f3efe6", "Source Serif 4", "IBM Plex Sans", "12px",
                    "0 16px 36px rgba(30,58,50,0.14)", "12px", true, false,
                    "Forest hero, then a split introduction, then brass-accent cards."),
            direction("creative-portfolio", "Creative Portfolio", "Direct and graphic", "Bold but sparse",
                    "#141414", "#fafafa", "#f0f0f0", "#ffffff", "#c4492c",
                    "#141414", "#1a1a1a", "#5e5e5e", "#fafafa", "Public Sans", "Fraunces", "0",
                    "none", "0", true, false,
                    "Black hero with a large title, then a project grid, then a quiet contact band."),
            direction("botanical-natural", "Botanical Natural", "Earthy and welcoming", "Rooted in place",
                    "linear-gradient(165deg, #2f4a3c, #4f6b58)", "#f4f1e8", "#e7efe6", "#fffaf2", "#b56a45",
                    "#2f4a3c", "#243028", "#667064", "#f4f1e8", "Newsreader", "Libre Franklin", "16px",
                    "0 14px 32px rgba(47,74,60,0.14)", "16px", true, false,
                    "Garden hero, then an image-and-text split, then linen detail cards."),
            direction("vintage-classic", "Vintage Classic", "Timeless and courteous", "Formal without stiffness",
                    "linear-gradient(180deg, #6b2d3c, #8d4454)", "#f8f1e3", "#f0e6d4", "#fff9ee", "#6b2d3c",
                    "#2c241c", "#2c241c", "#6e6256", "#f8f1e3", "Fraunces", "Source Serif 4", "4px",
                    "0 12px 28px rgba(107,45,60,0.12)", "4px", true, false,
                    "Wine hero, then a centered invitation, then a ruled details section."),
            direction("bold-contemporary", "Bold Contemporary", "Strong and graphic", "Confident",
                    "#111111", "#f6f6f4", "#ecece8", "#ffffff", "#f2c14e",
                    "#111111", "#161616", "#5c5c5c", "#f6f6f4", "Public Sans", "Public Sans", "0",
                    "0 10px 0 rgba(242,193,78,1)", "0", true, false,
                    "Black hero with a yellow accent, then a hard-edged split, then a stark detail row."),
            direction("scandinavian-minimal", "Scandinavian Minimal", "Calm and airy", "Spare",
                    "#f7f6f3", "#f7f6f3", "#e4e2dc", "#ffffff", "#3d4a4f",
                    "#3d4a4f", "#2c3336", "#6d767a", "#f7f6f3", "Public Sans", "Source Serif 4", "0",
                    "none", "0", false, false,
                    "Pale hero with one large title, then generous whitespace, then a simple fact row."),
            direction("art-deco", "Art Deco", "Glamorous and geometric", "Ornamental but controlled",
                    "linear-gradient(180deg, #0e3b34, #16120e)", "#f7f1e4", "#e6d3a3", "#fffaf1", "#0e3b34",
                    "#16120e", "#1c1814", "#6b5e48", "#f7f1e4", "Fraunces", "Source Serif 4", "0",
                    "0 0 0 1px rgba(14,59,52,0.4)", "0", true, false,
                    "Emerald hero, then a champagne panel, then framed cards with sharp corners."),
            direction("futuristic-gradient", "Futuristic Gradient", "Vivid and modern", "Forward, not noisy",
                    "linear-gradient(135deg, #241b4a, #6a4bc3)", "#f4f2fb", "#e7e2f6", "#ffffff", "#6a4bc3",
                    "#241b4a", "#241b4a", "#6d6780", "#f4f2fb", "IBM Plex Sans", "IBM Plex Sans", "20px",
                    "0 18px 40px rgba(36,27,74,0.16)", "20px", true, false,
                    "Violet gradient hero, then a light feature grid, then a soft closing band.")
    );

    public static List<DesignDirection> all() {
        return ALL;
    }

    public static DesignDirection resolve(com.fasterxml.jackson.databind.JsonNode plan, String category, com.fasterxml.jackson.databind.JsonNode answers) {
        String requested = plan == null ? "" : plan.path("direction").asText("");
        DesignDirection found = byId(requested);
        if (found == null) {
            found = forContext(category, answers);
        }
        return found.vary(salt(category, answers));
    }

    public ObjectNode json(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("name", name);
        node.put("mood", mood);
        node.put("personality", personality);
        node.put("heroBackground", heroBackground);
        node.put("pageBackground", pageBackground);
        node.put("alternateBackground", alternateBackground);
        node.put("surface", surface);
        node.put("accent", accent);
        node.put("heading", heading);
        node.put("text", text);
        node.put("muted", muted);
        node.put("lightText", lightText);
        node.put("displayFont", displayFont);
        node.put("bodyFont", bodyFont);
        node.put("radius", radius);
        node.put("shadow", shadow);
        node.put("imageRadius", imageRadius);
        node.put("heroDark", heroDark);
        node.put("pageDark", pageDark);
        node.put("composition", composition);
        return node;
    }

    public String instructions() {
        return """
                Design system (apply these values; do not invent a different palette):
                Concept: %s. Mood: %s. Personality: %s.
                Composition: %s
                Hero background: %s. Page background: %s. Alternate section background: %s. Card surface: %s.
                Accent: %s. Heading colour on light sections: %s. Body colour on light sections: %s. Muted: %s. Text on dark or gradient sections: %s.
                Display font: %s. Body font: %s. Corner radius: %s. Shadow: %s. Image radius: %s.
                The hero is the first SECTION. Give it the hero background, large padding, and the page title.
                Later SECTION backgrounds alternate between the page and alternate colours. Do not make every section white.
                Headings use the display font. Paragraphs use the body font.
                GROUP cards use the surface colour, the radius, and the shadow.
                A BUTTON uses the accent as its background.
                When photos were supplied, the hero or the gallery uses them. Two or more photos in one section belong in one CAROUSEL.
                Do not repeat the same alignment and the same column structure in consecutive sections.
                """.formatted(name, mood, personality, composition, heroBackground, pageBackground, alternateBackground, surface,
                accent, heading, text, muted, lightText, displayFont, bodyFont, radius, shadow, imageRadius);
    }

    private DesignDirection vary(int salt) {
        int mode = Math.floorMod(salt, 3);
        if (mode == 0) {
            return this;
        }
        String gradient = heroBackground.contains("deg")
                ? heroBackground.replace("160deg", mode == 1 ? "135deg" : "180deg").replace("180deg", mode == 1 ? "145deg" : "200deg")
                : heroBackground;
        if (mode == 1 && heroBackground.contains("160deg")) {
            gradient = heroBackground.replace("160deg", "135deg");
        }
        if (mode == 2 && heroBackground.contains("deg")) {
            gradient = heroBackground.replaceAll("\\d+deg", "200deg");
        }
        String nextRadius = mode == 2 && !"0".equals(radius) ? "50%" : radius;
        String nextImage = mode == 2 && !"0".equals(imageRadius) ? "50%" : imageRadius;
        if (mode == 1) {
            return direction(id, name, mood, personality, gradient, alternateBackground, pageBackground, surface, accent,
                    heading, text, muted, lightText, displayFont, bodyFont, radius, shadow, imageRadius, heroDark, pageDark, composition);
        }
        return direction(id, name, mood, personality, gradient, pageBackground, alternateBackground, surface, accent,
                heading, text, muted, lightText, displayFont, bodyFont, nextRadius, shadow, nextImage, heroDark, pageDark, composition);
    }

    private static DesignDirection forContext(String category, com.fasterxml.jackson.databind.JsonNode answers) {
        String style = answers == null ? "" : answers.path("style").asText("").toLowerCase(Locale.ROOT);
        String key = category == null ? "" : category.toUpperCase(Locale.ROOT);
        String id = switch (style) {
            case "luxury", "romantic" -> "luxury-romantic";
            case "minimal" -> "elegant-minimal";
            case "playful", "kids", "surprise" -> "playful-celebration";
            case "soft", "floral" -> "pastel-dream";
            case "bold" -> "bold-contemporary";
            case "editorial" -> "modern-editorial";
            case "cultural", "traditional", "classic" -> "vintage-classic";
            case "rustic" -> "botanical-natural";
            default -> switch (key) {
                case "WEDDING", "ANNIVERSARY" -> "luxury-romantic";
                case "BIRTHDAY" -> "playful-celebration";
                case "BABY" -> "pastel-dream";
                case "PORTFOLIO" -> "creative-portfolio";
                case "BUSINESS" -> "premium-business";
                case "CONFERENCE", "CORPORATE" -> "modern-corporate";
                default -> "modern-editorial";
            };
        };
        DesignDirection found = byId(id);
        return found == null ? ALL.get(0) : found;
    }

    private static int salt(String category, com.fasterxml.jackson.databind.JsonNode answers) {
        String basis = (category == null ? "" : category) + "|" + (answers == null ? "" : answers.toString());
        return Math.floorMod(basis.hashCode(), 97);
    }

    private static DesignDirection byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (DesignDirection direction : ALL) {
            if (direction.id.equalsIgnoreCase(id.trim())) {
                return direction;
            }
        }
        return null;
    }

    private static DesignDirection direction(String id, String name, String mood, String personality, String hero, String page, String alternate, String surface, String accent, String heading, String text, String muted, String light, String display, String body, String radius, String shadow, String imageRadius, boolean heroDark, boolean pageDark, String composition) {
        return new DesignDirection(id, name, mood, personality, hero, page, alternate, surface, accent, heading, text, muted, light, display, body, radius, shadow, imageRadius, heroDark, pageDark, composition);
    }
}

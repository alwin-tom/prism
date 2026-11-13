package com.prism.designlibrary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Original WebAux design concepts — structured patterns only, no third-party screenshots.
 */
final class DesignLibrarySeedCatalog {

    private DesignLibrarySeedCatalog() {
    }

    static List<Seed> originals() {
        List<Seed> seeds = new ArrayList<>();
        // Luxury / editorial wedding — 10
        seeds.add(wedding("lux-editorial-overlap", "Editorial Overlap Wedding", "luxury-editorial",
                "full-bleed-editorial", "asymmetric-editorial", "large-crops", "wide-narrow-alternation",
                "#F7F1E8", "#1A1410", "#8C6A4F", "Fraunces", "Manrope", "expressive", 9.2,
                "overlap-hero", "quote-break", "timeline-day", "gallery-editorial"));
        seeds.add(wedding("lux-split-serif", "Split Serif Invitation", "luxury-editorial",
                "split-editorial", "oversized-type", "subtle-overlay", "editorial-breaks",
                "#FAF7F2", "#121212", "#B08D57", "Playfair Display", "IBM Plex Sans", "balanced", 8.8,
                "split-hero", "story-chapter", "details-grid", "rsvp-cta"));
        seeds.add(wedding("lux-typographic-poster", "Typographic Poster Wedding", "bold-typographic",
                "typographic", "oversized-type", "supporting-image-blocks", "quiet-intervals",
                "#0F0F0F", "#F5F0E8", "#E8D5B5", "Fraunces", "Manrope", "expressive", 8.6,
                "poster-hero", "statement-quote", "schedule-stack", "photo-strip"));
        seeds.add(wedding("lux-cinematic-dusk", "Cinematic Dusk Ceremony", "cinematic-luxury",
                "full-bleed-editorial", "image-led", "subtle-overlay", "wide-narrow-alternation",
                "#1B1512", "#F4EDE4", "#C4A484", "Playfair Display", "IBM Plex Sans", "cinematic", 9.0,
                "dusk-hero", "venue-feature", "timeline-day", "carousel-moments"));
        seeds.add(wedding("lux-framed-portrait", "Framed Portrait Editorial", "luxury-editorial",
                "framed-editorial", "layered-media", "soft-frames", "editorial-breaks",
                "#F3EEE6", "#2A2420", "#9A7B5F", "Cormorant Garamond", "Manrope", "balanced", 8.4,
                "framed-hero", "couple-story", "people-row", "faq-soft"));
        seeds.add(wedding("lux-narrow-column", "Narrow Column Ceremony", "classic-formal",
                "typographic", "stacked-editorial", "gentle-crops", "quiet-intervals",
                "#FBF8F3", "#1C1A17", "#6E5A48", "Playfair Display", "IBM Plex Sans", "minimal", 8.1,
                "centred-formal-hero", "letter-block", "details-list", "divider-ornament"));
        seeds.add(wedding("lux-asymmetric-gallery", "Asymmetric Gallery Wedding", "contemporary-art",
                "split-editorial", "image-led", "graphic-crops", "wide-narrow-alternation",
                "#EEF0F2", "#111827", "#64748B", "Fraunces", "Manrope", "expressive", 8.7,
                "gallery-hero", "masonry-moments", "quote-break", "rsvp-cta"));
        seeds.add(wedding("lux-heritage-border", "Heritage Border Invitation", "royal-heritage",
                "framed-editorial", "organic-asymmetry", "soft-frames", "organic-breaks",
                "#F6EFE4", "#3A2A1C", "#A67C52", "Cormorant Garamond", "IBM Plex Sans", "balanced", 8.5,
                "ornament-hero", "family-names", "schedule-stack", "venue-map-link"));
        seeds.add(wedding("lux-horizontal-scroll-feel", "Horizontal Chapter Wedding", "luxury-editorial",
                "split-editorial", "asymmetric-editorial", "large-crops", "narrative-chapters",
                "#F8F4EE", "#1E1B18", "#7A5C45", "Fraunces", "Manrope", "cinematic", 8.9,
                "chapter-hero", "story-chapter", "photo-pair", "countdown-soft"));
        seeds.add(wedding("lux-quiet-luxury", "Quiet Luxury Suite", "modern-minimal",
                "typographic", "clear-blocks", "supporting-image-blocks", "quiet-intervals",
                "#FAFAF8", "#171717", "#525252", "Manrope", "IBM Plex Sans", "minimal", 8.3,
                "quiet-hero", "details-grid", "single-image", "rsvp-cta"));

        // Minimal / modern wedding — 5
        seeds.add(wedding("min-swiss-grid", "Swiss Grid Wedding", "modern-minimal",
                "typographic", "typographic-grid", "supporting-image-blocks", "quiet-intervals",
                "#FFFFFF", "#0A0A0A", "#171717", "IBM Plex Sans", "IBM Plex Sans", "minimal", 8.6,
                "grid-hero", "facts-row", "image-band", "faq-minimal"));
        seeds.add(wedding("min-left-rail", "Left Rail Modern", "modern-minimal",
                "split-editorial", "clear-blocks", "gentle-crops", "wide-narrow-alternation",
                "#F5F5F4", "#1C1917", "#44403C", "Manrope", "Manrope", "balanced", 8.2,
                "rail-hero", "itinerary", "venue-feature", "rsvp-cta"));
        seeds.add(wedding("min-mono-photo", "Mono Photo Modern", "modern-minimal",
                "full-bleed-editorial", "image-led", "large-crops", "editorial-breaks",
                "#F4F4F5", "#18181B", "#71717A", "IBM Plex Sans", "Manrope", "minimal", 8.4,
                "mono-hero", "short-story", "carousel-moments", "details-list"));
        seeds.add(wedding("min-type-only", "Type-Only Modern Invite", "bold-typographic",
                "typographic", "oversized-type", "supporting-image-blocks", "quiet-intervals",
                "#FAFAFA", "#09090B", "#A1A1AA", "Fraunces", "IBM Plex Sans", "expressive", 8.0,
                "type-hero", "meta-strip", "quote-break", "rsvp-cta"));
        seeds.add(wedding("min-soft-neutral", "Soft Neutral Modern", "modern-minimal",
                "split-organic", "stacked-editorial", "soft-frames", "alternating-image-text",
                "#F7F6F3", "#292524", "#78716C", "Manrope", "IBM Plex Sans", "balanced", 8.1,
                "soft-split-hero", "story-chapter", "people-row", "faq-soft"));

        // Romantic / botanical — 5
        seeds.add(seed("bot-garden-path", "Garden Path Romance", "WEDDING", "botanical-romance",
                "framed-editorial", "organic-asymmetry", "organic-masks", "alternating-image-text",
                "#F3F6F1", "#243024", "#6B8F71", "Cormorant Garamond", "Manrope", "balanced", 8.7,
                "garden-hero", "story-chapter", "timeline-day", "gallery-editorial",
                new String[]{"romantic", "garden", "soft"}, new String[]{"RSVP_FORM", "MAP_EMBED"}));
        seeds.add(seed("bot-petal-wash", "Petal Wash Invitation", "WEDDING", "botanical-romance",
                "illustrative-title", "soft-panels", "soft-frames", "organic-breaks",
                "#FBF5F7", "#3F2A32", "#C48B9F", "Fraunces", "IBM Plex Sans", "expressive", 8.3,
                "wash-hero", "letter-block", "schedule-stack", "rsvp-cta",
                new String[]{"floral", "pastel", "intimate"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("bot-anniversary-vine", "Anniversary Vine", "ANNIVERSARY", "botanical-romance",
                "split-organic", "layered-media", "soft-frames", "narrative-chapters",
                "#F6F1EA", "#2C241C", "#8F6B4A", "Playfair Display", "Manrope", "balanced", 8.5,
                "vine-hero", "years-timeline", "quote-break", "photo-pair",
                new String[]{"anniversary", "warm", "organic"}, new String[]{"MAP_EMBED"}));
        seeds.add(seed("bot-baby-meadow", "Baby Meadow", "BABY", "warm-storybook",
                "illustrative-title", "illustrative-chapters", "soft-frames", "alternating-panels",
                "#F7F3FF", "#3B2F4A", "#A78BFA", "Fraunces", "Manrope", "playful", 8.2,
                "meadow-hero", "story-chapter", "countdown-soft", "faq-soft",
                new String[]{"baby", "soft", "storybook"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("bot-engagement-bloom", "Engagement Bloom", "WEDDING", "botanical-romance",
                "framed-portrait", "organic-asymmetry", "gentle-crops", "alternating-image-text",
                "#FFF7ED", "#431407", "#EA580C", "Cormorant Garamond", "IBM Plex Sans", "balanced", 8.0,
                "bloom-hero", "proposal-story", "details-grid", "gallery-editorial",
                new String[]{"engagement", "warm", "celebration"}, new String[]{"RSVP_FORM", "MAP_EMBED"}));

        // Birthday / celebration — 5
        seeds.add(seed("bday-bold-confetti", "Bold Birthday Burst", "BIRTHDAY", "playful-contemporary",
                "oversized-title", "high-contrast", "graphic-crops", "alternating-panels",
                "#FFF7ED", "#7C2D12", "#F97316", "Fraunces", "Manrope", "playful", 8.4,
                "burst-hero", "party-details", "carousel-moments", "rsvp-cta",
                new String[]{"birthday", "bold", "fun"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("bday-storybook-cake", "Storybook Cake Day", "BIRTHDAY", "warm-storybook",
                "illustrative-title", "soft-panels", "soft-frames", "narrative-chapters",
                "#FFF1F2", "#4C0519", "#FB7185", "Playfair Display", "IBM Plex Sans", "playful", 8.1,
                "cake-hero", "story-chapter", "timeline-day", "faq-soft",
                new String[]{"kids", "pastel", "party"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("bday-neon-type", "Neon Type Birthday", "BIRTHDAY", "bold-typographic",
                "typographic-poster", "oversized-type", "supporting-image-blocks", "quiet-intervals",
                "#0B1020", "#F8FAFC", "#22D3EE", "Manrope", "IBM Plex Sans", "expressive", 8.3,
                "neon-hero", "meta-strip", "photo-strip", "rsvp-cta",
                new String[]{"night", "bold", "adult"}, new String[]{"MAP_EMBED"}));
        seeds.add(seed("bday-milestone-clean", "Milestone Clean", "BIRTHDAY", "modern-minimal",
                "typographic", "clear-blocks", "gentle-crops", "quiet-intervals",
                "#F8FAFC", "#0F172A", "#334155", "IBM Plex Sans", "Manrope", "minimal", 7.9,
                "clean-hero", "facts-row", "single-image", "details-list",
                new String[]{"milestone", "clean"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("bday-family-album", "Family Album Birthday", "BIRTHDAY", "warm-storybook",
                "framed-portrait", "story-flow", "soft-frames", "alternating-panels",
                "#FFFBEB", "#422006", "#D97706", "Fraunces", "Manrope", "balanced", 8.0,
                "album-hero", "carousel-moments", "schedule-stack", "quote-break",
                new String[]{"family", "album", "warm"}, new String[]{"RSVP_FORM"}));

        // Anniversary / engagement — 5
        seeds.add(seed("ann-editorial-years", "Editorial Years", "ANNIVERSARY", "luxury-editorial",
                "split-editorial", "asymmetric-editorial", "large-crops", "editorial-breaks",
                "#F5F0E8", "#1C1917", "#A8A29E", "Playfair Display", "Manrope", "balanced", 8.6,
                "years-hero", "years-timeline", "photo-pair", "rsvp-cta",
                new String[]{"anniversary", "editorial"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("ann-classic-formal", "Classic Formal Anniversary", "ANNIVERSARY", "classic-formal",
                "centred-formal-hero", "stacked-editorial", "gentle-crops", "quiet-intervals",
                "#FAF7F2", "#1A1A1A", "#6B7280", "Cormorant Garamond", "IBM Plex Sans", "minimal", 8.2,
                "formal-hero", "letter-block", "details-grid", "divider-ornament",
                new String[]{"formal", "classic"}, new String[]{"MAP_EMBED"}));
        seeds.add(seed("ann-cinematic-night", "Cinematic Night Anniversary", "ANNIVERSARY", "cinematic-luxury",
                "full-bleed-editorial", "image-led", "subtle-overlay", "wide-narrow-alternation",
                "#0C0A09", "#FAFAF9", "#D6D3D1", "Fraunces", "Manrope", "cinematic", 8.8,
                "night-hero", "venue-feature", "quote-break", "gallery-editorial",
                new String[]{"night", "cinematic"}, new String[]{"RSVP_FORM"}));
        seeds.add(seed("eng-modern-promise", "Modern Promise", "WEDDING", "modern-minimal",
                "split-editorial", "clear-blocks", "supporting-image-blocks", "quiet-intervals",
                "#F8FAFC", "#0F172A", "#64748B", "Manrope", "IBM Plex Sans", "balanced", 8.1,
                "promise-hero", "story-chapter", "details-list", "rsvp-cta",
                new String[]{"engagement", "modern"}, new String[]{"RSVP_FORM", "MAP_EMBED"}));
        seeds.add(seed("eng-art-gallery", "Engagement Gallery", "PORTFOLIO", "contemporary-art",
                "gallery-hero", "image-led", "graphic-crops", "wide-narrow-alternation",
                "#FAFAFA", "#171717", "#525252", "Fraunces", "Manrope", "expressive", 8.4,
                "gallery-hero", "masonry-moments", "statement-quote", "meta-strip",
                new String[]{"engagement", "gallery", "art"}, new String[]{}));

        // Corporate / professional — 5
        seeds.add(seed("corp-trust-band", "Trust Band Corporate", "CORPORATE", "classic-formal",
                "typographic", "clear-blocks", "supporting-image-blocks", "quiet-intervals",
                "#F8FAFC", "#0F172A", "#0EA5E9", "IBM Plex Sans", "Manrope", "minimal", 8.5,
                "trust-hero", "agenda-timeline", "speakers-row", "faq-minimal",
                new String[]{"corporate", "trust"}, new String[]{"MAP_EMBED", "REGISTRATION_FORM"}));
        seeds.add(seed("corp-data-editorial", "Data Editorial Event", "CONFERENCE", "modern-minimal",
                "split-editorial", "typographic-grid", "graphic-crops", "editorial-breaks",
                "#FFFFFF", "#111827", "#2563EB", "Manrope", "IBM Plex Sans", "balanced", 8.6,
                "data-hero", "agenda-timeline", "feature-list", "rsvp-cta",
                new String[]{"conference", "editorial"}, new String[]{"REGISTRATION_FORM"}));
        seeds.add(seed("corp-high-contrast", "High Contrast Summit", "CONFERENCE", "bold-typographic",
                "oversized-title", "high-contrast", "supporting-image-blocks", "quiet-intervals",
                "#0B1220", "#F8FAFC", "#38BDF8", "Manrope", "IBM Plex Sans", "expressive", 8.3,
                "summit-hero", "speakers-row", "schedule-stack", "faq-minimal",
                new String[]{"summit", "bold"}, new String[]{"REGISTRATION_FORM", "MAP_EMBED"}));
        seeds.add(seed("biz-premium-card", "Premium Business Landing", "BUSINESS", "classic-formal",
                "typographic", "stacked-editorial", "gentle-crops", "quiet-intervals",
                "#F9FAFB", "#111827", "#4B5563", "IBM Plex Sans", "Manrope", "minimal", 8.0,
                "biz-hero", "feature-list", "testimonial-quote", "contact-cta",
                new String[]{"business", "premium"}, new String[]{"CONTACT_FORM"}));
        seeds.add(seed("corp-warm-professional", "Warm Professional Meetup", "CORPORATE", "modern-minimal",
                "split-editorial", "clear-blocks", "soft-frames", "alternating-image-text",
                "#FAF7F2", "#1C1917", "#B45309", "Manrope", "IBM Plex Sans", "balanced", 7.9,
                "meetup-hero", "agenda-timeline", "people-row", "venue-map-link",
                new String[]{"meetup", "warm"}, new String[]{"RSVP_FORM", "MAP_EMBED"}));

        // Creative / experimental — 5
        seeds.add(seed("exp-portfolio-slab", "Portfolio Slab", "PORTFOLIO", "contemporary-art",
                "gallery-hero", "image-led", "graphic-crops", "wide-narrow-alternation",
                "#F4F4F5", "#09090B", "#A1A1AA", "Fraunces", "Manrope", "expressive", 8.7,
                "slab-hero", "case-grid", "statement-quote", "contact-cta",
                new String[]{"portfolio", "experimental"}, new String[]{}));
        seeds.add(seed("exp-diagonal-cut", "Diagonal Cut Landing", "CUSTOM", "bold-typographic",
                "typographic-poster", "oversized-type", "graphic-crops", "editorial-breaks",
                "#FFF7ED", "#7C2D12", "#EA580C", "Manrope", "IBM Plex Sans", "expressive", 8.2,
                "diagonal-hero", "feature-list", "photo-strip", "rsvp-cta",
                new String[]{"experimental", "landing"}, new String[]{}));
        seeds.add(seed("exp-magazine-spread", "Magazine Spread Event", "CUSTOM", "luxury-editorial",
                "asymmetric-editorial", "layered-media", "large-crops", "editorial-breaks",
                "#F5F5F4", "#1C1917", "#78716C", "Playfair Display", "Manrope", "cinematic", 8.5,
                "spread-hero", "story-chapter", "masonry-moments", "quote-break",
                new String[]{"magazine", "editorial"}, new String[]{}));
        seeds.add(seed("exp-kinetic-type", "Kinetic Type Invite", "CUSTOM", "bold-typographic",
                "oversized-title", "high-contrast", "supporting-image-blocks", "quiet-intervals",
                "#0A0A0A", "#FAFAFA", "#F43F5E", "Fraunces", "IBM Plex Sans", "playful", 8.1,
                "kinetic-hero", "meta-strip", "single-image", "faq-minimal",
                new String[]{"type", "kinetic"}, new String[]{}));
        seeds.add(seed("exp-soft-brutal", "Soft Brutalist Event", "CORPORATE", "contemporary-art",
                "typographic", "clear-blocks", "graphic-crops", "quiet-intervals",
                "#E7E5E4", "#1C1917", "#44403C", "IBM Plex Sans", "Manrope", "minimal", 8.0,
                "brutal-hero", "feature-list", "agenda-timeline", "contact-cta",
                new String[]{"brutalist", "soft"}, new String[]{"REGISTRATION_FORM"}));

        return seeds;
    }

    static List<Seed> inspirationLinks() {
        return List.of(
                inspiration("insp-awwwards", "Awwwards — award-winning sites", "GENERAL", "contemporary-art",
                        "https://www.awwwards.com/", "Awwwards", new String[]{"awards", "experimental", "web"}),
                inspiration("insp-landbook", "Land-book — landing pages", "GENERAL", "modern-minimal",
                        "https://land-book.com/", "Land-book", new String[]{"landing", "hero", "sections"}),
                inspiration("insp-godly", "Godly — distinctive websites", "GENERAL", "bold-typographic",
                        "https://godly.website/", "Godly", new String[]{"contemporary", "interaction"}),
                inspiration("insp-mobbin", "Mobbin — mobile UI patterns", "GENERAL", "modern-minimal",
                        "https://mobbin.com/", "Mobbin", new String[]{"mobile", "navigation", "forms"}),
                inspiration("insp-stitch", "Google Stitch — design explorations", "GENERAL", "luxury-editorial",
                        "https://stitch.withgoogle.com/", "Google Stitch", new String[]{"stitch", "concepts"})
        );
    }

    private static Seed wedding(String key, String name, String archetype, String hero, String composition,
                                String image, String rhythm, String bg, String text, String accent,
                                String display, String body, String density, double quality, String... sections) {
        return seed(key, name, "WEDDING", archetype, hero, composition, image, rhythm, bg, text, accent, display, body, density, quality,
                sections[0], sections[1], sections[2], sections[3],
                new String[]{"wedding", archetype}, new String[]{"RSVP_FORM", "MAP_EMBED"});
    }

    private static Seed seed(String key, String name, String category, String archetype, String hero, String composition,
                             String image, String rhythm, String bg, String text, String accent,
                             String display, String body, String density, double quality,
                             String s1, String s2, String s3, String s4, String[] tags, String[] missing) {
        return new Seed(key, name, "Original WebAux concept: " + name + ".", "APPROVED", "ORIGINAL", category, archetype,
                new String[]{tags.length > 0 ? tags[0] : "designed", density}, tags, bg, text, accent, display, body,
                hero, composition, image, rhythm, density, s1, s2, s3, s4, missing,
                "WebAux original design concept", "WEBAUX_ORIGINAL", null, BigDecimal.valueOf(quality));
    }

    private static Seed inspiration(String key, String name, String category, String archetype, String url, String source, String[] tags) {
        return new Seed(key, name, "External inspiration link only. Do not copy screenshots without permission.",
                "APPROVED", "INSPIRATION_LINK", category, archetype, tags, tags,
                "#F8FAFC", "#0F172A", "#64748B", "Manrope", "IBM Plex Sans",
                "typographic", "clear-blocks", "supporting-image-blocks", "quiet-intervals", "minimal",
                "link-only", "n/a", "n/a", "n/a", new String[0],
                source + " — inspiration URL only", "EXTERNAL_INSPIRATION", url, BigDecimal.valueOf(6.0));
    }

    record Seed(
            String seedKey,
            String name,
            String description,
            String status,
            String sourceType,
            String eventCategory,
            String archetypeId,
            String[] mood,
            String[] styleTags,
            String background,
            String text,
            String accent,
            String displayFont,
            String bodyFont,
            String heroPattern,
            String composition,
            String imageTreatment,
            String sectionRhythm,
            String density,
            String section1,
            String section2,
            String section3,
            String section4,
            String[] missingComponents,
            String attribution,
            String usagePermission,
            String inspirationUrl,
            BigDecimal qualityScore
    ) {
    }
}

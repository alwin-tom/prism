package com.prism.designlibrary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(40)
public class DesignLibrarySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DesignLibrarySeeder.class);

    private final DesignReferenceRepository references;
    private final DesignReferenceSectionRepository sections;
    private final ObjectMapper mapper;

    public DesignLibrarySeeder(DesignReferenceRepository references, DesignReferenceSectionRepository sections, ObjectMapper mapper) {
        this.references = references;
        this.sections = sections;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int inserted = 0;
        for (DesignLibrarySeedCatalog.Seed seed : DesignLibrarySeedCatalog.originals()) {
            if (insert(seed)) {
                inserted++;
            }
        }
        for (DesignLibrarySeedCatalog.Seed seed : DesignLibrarySeedCatalog.inspirationLinks()) {
            if (insert(seed)) {
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("Seeded {} design reference records", inserted);
        }
    }

    private boolean insert(DesignLibrarySeedCatalog.Seed seed) {
        if (references.existsBySeedKey(seed.seedKey())) {
            return false;
        }
        DesignReference row = new DesignReference();
        row.seedKey = seed.seedKey();
        row.name = seed.name();
        row.description = seed.description();
        row.status = seed.status();
        row.sourceType = seed.sourceType();
        row.eventCategory = seed.eventCategory();
        row.archetypeId = seed.archetypeId();
        row.mood = seed.mood();
        row.styleTags = seed.styleTags();
        row.colourPalette = palette(seed);
        row.typography = typography(seed);
        row.layout = layout(seed);
        row.patternJson = pattern(seed);
        row.componentMap = components(seed);
        row.missingComponents = seed.missingComponents();
        row.keywords = keywords(seed);
        row.attribution = seed.attribution();
        row.usagePermission = seed.usagePermission();
        row.inspirationUrl = seed.inspirationUrl();
        row.qualityScore = seed.qualityScore();
        row.retrievalCount = 0;
        DesignReference saved = references.save(row);
        if (!"INSPIRATION_LINK".equals(seed.sourceType())) {
            saveSections(saved, seed);
        }
        return true;
    }

    private void saveSections(DesignReference saved, DesignLibrarySeedCatalog.Seed seed) {
        List<String[]> defs = List.of(
                new String[]{"hero", "Open with a distinctive first viewport", seed.heroPattern(), seed.section1()},
                new String[]{"story", "Carry the narrative without repeating the hero", "stacked-editorial", seed.section2()},
                new String[]{"details", "Present factual event details from answers only", seed.composition(), seed.section3()},
                new String[]{"close", "Close with a clear next step", "cta-band", seed.section4()}
        );
        int order = 0;
        for (String[] def : defs) {
            DesignReferenceSection section = new DesignReferenceSection();
            section.referenceId = saved.id;
            section.sectionKey = def[0];
            section.purpose = def[1];
            section.composition = def[2];
            ObjectNode pattern = mapper.createObjectNode();
            pattern.put("variant", def[3]);
            pattern.put("density", seed.density());
            section.patternJson = pattern;
            section.componentMap = sectionComponents(def[0]);
            section.sortOrder = order++;
            sections.save(section);
        }
    }

    private ObjectNode palette(DesignLibrarySeedCatalog.Seed seed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("background", seed.background());
        node.put("text", seed.text());
        node.put("accent", seed.accent());
        node.put("surface", seed.background());
        node.put("mutedText", seed.text());
        return node;
    }

    private ObjectNode typography(DesignLibrarySeedCatalog.Seed seed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("display", seed.displayFont());
        node.put("heading", seed.displayFont());
        node.put("body", seed.bodyFont());
        node.put("density", seed.density());
        return node;
    }

    private ObjectNode layout(DesignLibrarySeedCatalog.Seed seed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("heroPattern", seed.heroPattern());
        node.put("composition", seed.composition());
        node.put("sectionRhythm", seed.sectionRhythm());
        node.put("contentWidth", "expressive".equals(seed.density()) ? "1280px" : "1120px");
        node.put("alignmentBias", seed.heroPattern().contains("typographic") || seed.heroPattern().contains("centred") ? "center" : "left");
        return node;
    }

    private ObjectNode pattern(DesignLibrarySeedCatalog.Seed seed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("hero", seed.heroPattern());
        node.put("imageTreatment", seed.imageTreatment());
        node.put("sectionRhythm", seed.sectionRhythm());
        node.put("density", seed.density());
        node.put("motion", "playful".equals(seed.density()) ? "playful" : "cinematic".equals(seed.density()) ? "cinematic" : "subtle");
        ObjectNode responsive = node.putObject("responsive");
        responsive.put("mobileStack", true);
        responsive.put("heroHeight", "expressive".equals(seed.density()) ? "full" : "tall");
        ArrayNode avoid = node.putArray("avoid");
        avoid.add("repeated-card-grids");
        avoid.add("generic-centred-sections");
        return node;
    }

    private ObjectNode components(DesignLibrarySeedCatalog.Seed seed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("hero", "SECTION+HEADING+PARAGRAPH+IMAGE|FLEX");
        node.put("story", "SECTION+HEADING+PARAGRAPH");
        node.put("media", "CAROUSEL|IMAGE|GRID");
        node.put("schedule", "TIMELINE+TIMELINE_ITEM");
        node.put("faq", "FAQ_ACCORDION+FAQ_ITEM");
        node.put("statement", "QUOTE");
        node.put("countdown", "COUNTDOWN");
        node.put("cta", "BUTTON");
        node.put("seedKey", seed.seedKey());
        return node;
    }

    private ObjectNode sectionComponents(String key) {
        ObjectNode node = mapper.createObjectNode();
        switch (key) {
            case "hero" -> {
                node.putArray("types").add("SECTION").add("HEADING").add("PARAGRAPH").add("IMAGE").add("FLEX");
            }
            case "story" -> node.putArray("types").add("SECTION").add("HEADING").add("PARAGRAPH").add("QUOTE");
            case "details" -> node.putArray("types").add("SECTION").add("TIMELINE").add("TIMELINE_ITEM").add("GRID").add("COUNTDOWN");
            default -> node.putArray("types").add("SECTION").add("BUTTON").add("FAQ_ACCORDION").add("CAROUSEL");
        }
        return node;
    }

    private String[] keywords(DesignLibrarySeedCatalog.Seed seed) {
        return new String[]{
                seed.eventCategory().toLowerCase(),
                seed.archetypeId(),
                seed.heroPattern(),
                seed.composition(),
                seed.density()
        };
    }
}

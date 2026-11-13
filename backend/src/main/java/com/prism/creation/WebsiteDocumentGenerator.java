package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.designlibrary.DesignReferenceRetriever;
import com.prism.editor.DocumentValidator;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class WebsiteDocumentGenerator {

    private final ObjectMapper mapper;
    private final DocumentValidator validator;
    private final DocumentNormalizer normalizer;
    private final ArchetypeCatalog archetypes;
    private final CreativeSchemas creativeSchemas;
    private final PrismProperties properties;
    private final DesignReferenceRetriever designReferences;

    public WebsiteDocumentGenerator(
            ObjectMapper mapper,
            ArchetypeCatalog archetypes,
            CreativeSchemas creativeSchemas,
            PrismProperties properties,
            ObjectProvider<DesignReferenceRetriever> designReferences
    ) {
        this.mapper = mapper;
        this.validator = new DocumentValidator(mapper);
        this.normalizer = new DocumentNormalizer(mapper);
        this.archetypes = archetypes;
        this.creativeSchemas = creativeSchemas;
        this.properties = properties;
        this.designReferences = designReferences.getIfAvailable();
    }

    /** Test helper: legacy pipeline only. */
    static WebsiteDocumentGenerator legacyForTests(ObjectMapper mapper) {
        PrismProperties properties = new PrismProperties();
        properties.getCreation().setCreativeV2Enabled(false);
        return new WebsiteDocumentGenerator(mapper, new ArchetypeCatalog(mapper), new CreativeSchemas(mapper), properties, emptyRetriever());
    }

    private static ObjectProvider<DesignReferenceRetriever> emptyRetriever() {
        return new ObjectProvider<>() {
            @Override
            public DesignReferenceRetriever getObject() {
                return null;
            }
        };
    }

    public Generated generate(OpenRouterClient client, PrismProperties.OpenRouter settings, String category, JsonNode answers, Set<String> mediaIds, Consumer<String> stage) {
        return generate(client, settings, category, answers, mediaIds, stage, null);
    }

    public Generated generate(OpenRouterClient client, PrismProperties.OpenRouter settings, String category, JsonNode answers, Set<String> mediaIds, Consumer<String> stage, UUID userId) {
        if (properties.getCreation().isCreativeV2Enabled()) {
            return generateV2(client, settings, category, answers, mediaIds, stage, userId);
        }
        return generateLegacy(client, settings, category, answers, mediaIds, stage);
    }

    private Generated generateV2(OpenRouterClient client, PrismProperties.OpenRouter settings, String category, JsonNode answers, Set<String> mediaIds, Consumer<String> stage, UUID userId) {
        AttemptBudget budget = AttemptBudget.from(settings);
        ArrayNode stages = mapper.createArrayNode();
        JsonNode planNode = null;
        JsonNode last = null;
        JsonNode creative = null;
        JsonNode blueprint = null;
        JsonNode retrieved = mapper.createArrayNode();
        String retrievalMode = "none";
        int input = 0;
        int output = 0;
        int repairs = 0;
        try {
            String preferred = answers == null ? "" : answers.path("visualDirection").asText("");
            if (preferred.isBlank() && answers != null) {
                preferred = answers.path("style").asText("");
            }
            stage.accept("PLANNING");
            boolean library = properties.getCreation().isDesignLibraryEnabled() && designReferences != null;
            if (library) {
                DesignReferenceRetriever.Result retrieval = designReferences.retrieve(category, preferred, answers, userId);
                retrieved = retrieval.compact();
                retrievalMode = retrieval.mode();
                ObjectNode retrievalNote = stages.addObject();
                retrievalNote.put("stage", "DESIGN_RETRIEVAL");
                retrievalNote.put("mode", retrievalMode);
                retrievalNote.put("count", retrieval.ids().size());
                OpenRouterClient.Completion directorCall = call(client, settings, model(settings.getPlanningModel(), settings.getGenerationModel()), settings.getPlanningFallbacks(), budget,
                        PromptBuilder.designDirector(category, answers, mediaIds, archetypes.promptCatalog(category), preferred, retrieved),
                        creativeSchemas.director(), CreativeSchemas.DIRECTOR_SCHEMA);
                note(stages, "DESIGN_DIRECTOR", directorCall);
                JsonNode director = directorCall.content();
                input += directorCall.inputTokens();
                output += directorCall.outputTokens();
                String directorProblem = PrismSchemaCheck.problem(creativeSchemas.director(), director);
                if (directorProblem != null) {
                    throw new GenerationFailure(directorProblem, null, director, input, output, 0, budget, usage(budget, stages, null, retrieved, retrievalMode));
                }
                creative = directorToCreative(director);
                blueprint = directorToBlueprint(director);
            } else {
                OpenRouterClient.Completion directionCall = call(client, settings, model(settings.getPlanningModel(), settings.getGenerationModel()), settings.getPlanningFallbacks(), budget,
                        PromptBuilder.creativeDirection(category, answers, mediaIds, archetypes.promptCatalog(category), preferred),
                        creativeSchemas.direction(), CreativeSchemas.DIRECTION_SCHEMA);
                note(stages, "CREATIVE_DIRECTION", directionCall);
                creative = directionCall.content();
                input += directionCall.inputTokens();
                output += directionCall.outputTokens();
                String directionProblem = PrismSchemaCheck.problem(creativeSchemas.direction(), creative);
                if (directionProblem != null) {
                    throw new GenerationFailure(directionProblem, null, creative, input, output, 0, budget, usage(budget, stages, null, retrieved, retrievalMode));
                }
            }
            String archetypeId = creative.path("archetypeId").asText("");
            if (archetypes.byId(archetypeId).isEmpty()) {
                archetypeId = archetypes.resolvePreference(preferred, category, answers);
                if (creative instanceof ObjectNode object) {
                    object.put("archetypeId", archetypeId);
                    creative = object;
                }
            }
            boolean userPreference = preferred != null && !preferred.isBlank() && !"ai".equalsIgnoreCase(preferred) && !"auto".equalsIgnoreCase(preferred);
            if (creative instanceof ObjectNode object) {
                object.put("userPreferenceApplied", userPreference && preferred.equalsIgnoreCase(archetypeId));
                if (answers != null) {
                    String richness = answers.path("visualRichness").asText("");
                    if (!richness.isBlank()) {
                        object.put("visualComplexity", richness);
                    }
                    String motion = answers.path("motionPreference").asText("");
                    if (!motion.isBlank()) {
                        object.put("motionStrategy", motion);
                    }
                }
            }

            ObjectNode syntheticPlan = mapper.createObjectNode();
            syntheticPlan.put("title", answers == null ? "Untitled website" : firstTitle(answers));
            syntheticPlan.put("description", "");
            syntheticPlan.put("style", answers == null ? "" : answers.path("style").asText(""));
            syntheticPlan.put("voice", creative.path("rationale").asText("Write in a clear, welcoming voice."));
            syntheticPlan.put("direction", archetypes.directionId(archetypeId));
            syntheticPlan.put("concept", archetypeId);
            syntheticPlan.put("mood", creative.path("mood").isArray() && creative.path("mood").size() > 0 ? creative.path("mood").get(0).asText("") : "");
            syntheticPlan.putArray("sections");
            syntheticPlan.putArray("patterns");
            DesignDirection direction = DesignDirection.resolve(syntheticPlan, category, answers);
            ObjectNode designSystem = DesignSystemTokens.fromDirection(mapper, direction, archetypeId, creative);
            if (syntheticPlan instanceof ObjectNode planObject) {
                planObject.set("designSystem", direction.json(mapper));
                planNode = planObject;
            }

            if (blueprint == null) {
                String archetypeGuide = archetypes.byId(archetypeId).map(JsonNode::toString).orElse(archetypeId);
                OpenRouterClient.Completion blueprintCall = call(client, settings, model(settings.getPlanningModel(), settings.getGenerationModel()), settings.getPlanningFallbacks(), budget,
                        PromptBuilder.visualBlueprint(category, answers, mediaIds, creative, designSystem, archetypeGuide),
                        creativeSchemas.blueprint(), CreativeSchemas.BLUEPRINT_SCHEMA);
                note(stages, "VISUAL_BLUEPRINT", blueprintCall);
                blueprint = blueprintCall.content();
                input += blueprintCall.inputTokens();
                output += blueprintCall.outputTokens();
                String blueprintProblem = PrismSchemaCheck.problem(creativeSchemas.blueprint(), blueprint);
                if (blueprintProblem != null) {
                    throw new GenerationFailure(blueprintProblem, planNode, blueprint, input, output, 0, budget, usage(budget, stages, null, retrieved, retrievalMode));
                }
            } else {
                ObjectNode blueprintNote = stages.addObject();
                blueprintNote.put("stage", "VISUAL_BLUEPRINT");
                blueprintNote.put("source", "design-director");
            }
            if (planNode instanceof ObjectNode planObject && blueprint.path("sections").isArray()) {
                for (JsonNode section : blueprint.path("sections")) {
                    planObject.withArray("sections").add(section.path("purpose").asText(section.path("id").asText("Section")));
                    planObject.withArray("patterns").add(section.path("composition").asText("stacked-editorial"));
                }
                String heroPattern = blueprint.path("hero").path("pattern").asText("full-width-hero");
                planObject.withArray("patterns").insert(0, heroPattern);
            }
            applyDirectorDesignHints(designSystem, creative);

            stage.accept("GENERATING");
            String documentPrompt = retrieved.isEmpty()
                    ? PromptBuilder.documentFromBlueprint(category, answers, mediaIds, planNode, blueprint, designSystem)
                    : PromptBuilder.documentFromBlueprintWithReferences(category, answers, mediaIds, planNode, blueprint, designSystem, retrieved);
            OpenRouterClient.Completion draft = call(client, settings, model(settings.getGenerationModel(), settings.getPlanningModel()), settings.getGenerationFallbacks(), budget,
                    documentPrompt,
                    PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA);
            note(stages, "GENERATING", draft);
            input += draft.inputTokens();
            output += draft.outputTokens();
            String usedModel = draft.model();
            last = draft.content();
            Attempt attempt = accept(draft.content(), mediaIds, designSystem, creative);
            int repairsAllowed = Math.min(2, Math.max(0, settings.getMaxRepairAttempts()));
            while (attempt.document() == null && repairs < repairsAllowed) {
                repairs++;
                stage.accept("REPAIRING");
                OpenRouterClient.Completion repair = call(client, settings, model(settings.getRepairModel(), settings.getGenerationModel()), settings.getRepairFallbacks(), budget,
                        PromptBuilder.repair(attempt.problems(), last), PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA);
                note(stages, "REPAIRING", repair);
                input += repair.inputTokens();
                output += repair.outputTokens();
                usedModel = repair.model();
                last = repair.content();
                attempt = accept(repair.content(), mediaIds, designSystem, creative);
            }
            stage.accept("VALIDATING");
            if (attempt.document() == null) {
                throw new GenerationFailure(attempt.problems(), planNode, last, input, output, repairs, budget, usage(budget, stages, null, retrieved, retrievalMode));
            }

            ObjectNode qa = DeterministicVisualQa.assess(mapper, attempt.document(), blueprint, archetypeId);
            List<String> qaNotes = DeterministicVisualQa.actionable(qa);
            if (!qaNotes.isEmpty() && repairs == 0 && repairsAllowed > 0) {
                stage.accept("REPAIRING");
                OpenRouterClient.Completion refine = call(client, settings, model(settings.getRepairModel(), settings.getGenerationModel()), settings.getRepairFallbacks(), budget,
                        PromptBuilder.refine(qaNotes, attempt.document(), category, answers, planNode), PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA);
                note(stages, "QA_REFINE", refine);
                input += refine.inputTokens();
                output += refine.outputTokens();
                usedModel = refine.model();
                last = refine.content();
                Attempt refined = accept(refine.content(), mediaIds, designSystem, creative);
                if (refined.document() != null) {
                    attempt = refined;
                    repairs++;
                    qa = DeterministicVisualQa.assess(mapper, attempt.document(), blueprint, archetypeId);
                }
            }

            ObjectNode directed = TypographyApplicator.apply(
                    DesignApplicator.apply(
                            ReplyButtonEnricher.apply(CountdownEnricher.apply(attempt.document(), answers)),
                            direction
                    ),
                    planNode,
                    direction.id()
            );
            directed.set("designTokens", DesignSystemTokens.sanitize(mapper, designSystem));
            ObjectNode creativeMeta = directed.putObject("creative");
            creativeMeta.put("archetypeId", archetypeId);
            creativeMeta.put("rationale", creative.path("rationale").asText(""));
            creativeMeta.put("promptVersion", CreativeSchemas.PROMPT_VERSION);
            creativeMeta.put("motionStrategy", creative.path("motionStrategy").asText("subtle"));
            creativeMeta.put("visualComplexity", creative.path("visualComplexity").asText("balanced"));
            directed = ResponsiveDefaults.apply(directed);
            DesignQuality.Result quality = DesignQuality.assess(directed, direction);
            ObjectNode report = usage(budget, stages, qa, retrieved, retrievalMode);
            report.set("quality", quality.json(mapper));
            if (blueprint != null) {
                report.set("blueprint", blueprint);
            }
            report.put("promptVersion", CreativeSchemas.PROMPT_VERSION);
            report.put("archetypeId", archetypeId);
            report.put("catalogueVersion", archetypes.catalogueVersion());
            return new Generated(planNode, last, directed, attempt.warnings(), input, output, usedModel, repairs, budget.cost(), budget.latencyMs(), budget.requests(), report);
        } catch (OpenRouterException exception) {
            throw new GenerationFailure(exception.getMessage(), planNode, last, input, output, repairs, budget, usage(budget, stages, null, retrieved, retrievalMode));
        }
    }

    private Generated generateLegacy(OpenRouterClient client, PrismProperties.OpenRouter settings, String category, JsonNode answers, Set<String> mediaIds, Consumer<String> stage) {
        AttemptBudget budget = AttemptBudget.from(settings);
        com.fasterxml.jackson.databind.node.ArrayNode stages = mapper.createArrayNode();
        JsonNode planNode = null;
        JsonNode last = null;
        int input = 0;
        int output = 0;
        int repairs = 0;
        try {
            stage.accept("PLANNING");
            OpenRouterClient.Completion plan = call(client, settings, model(settings.getPlanningModel(), settings.getGenerationModel()), settings.getPlanningFallbacks(), budget, PromptBuilder.plan(category, answers, mediaIds), PrismSchemas.plan(mapper), PrismSchemas.PLAN_SCHEMA);
            note(stages, "PLANNING", plan);
            planNode = plan.content();
            input += plan.inputTokens();
            output += plan.outputTokens();
            String planProblem = PrismSchemaCheck.problem(PrismSchemas.plan(mapper), plan.content());
            if (planProblem != null) {
                throw new GenerationFailure(planProblem, planNode, planNode, input, output, 0, budget, usage(budget, stages, null));
            }
            DesignDirection direction = DesignDirection.resolve(planNode, category, answers);
            if (planNode instanceof ObjectNode planObject) {
                planObject.set("designSystem", direction.json(mapper));
                planNode = planObject;
            }
            stage.accept("GENERATING");
            OpenRouterClient.Completion draft = call(client, settings, model(settings.getGenerationModel(), settings.getPlanningModel()), settings.getGenerationFallbacks(), budget, PromptBuilder.document(category, answers, mediaIds, planNode), PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA);
            note(stages, "GENERATING", draft);
            input += draft.inputTokens();
            output += draft.outputTokens();
            String usedModel = draft.model();
            last = draft.content();
            Attempt attempt = accept(draft.content(), mediaIds, null, null);
            int repairsAllowed = Math.min(2, Math.max(0, settings.getMaxRepairAttempts()));
            while (attempt.document() == null && repairs < repairsAllowed) {
                repairs++;
                stage.accept("REPAIRING");
                OpenRouterClient.Completion repair = call(client, settings, model(settings.getRepairModel(), settings.getGenerationModel()), settings.getRepairFallbacks(), budget, PromptBuilder.repair(attempt.problems(), last), PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA);
                note(stages, "REPAIRING", repair);
                input += repair.inputTokens();
                output += repair.outputTokens();
                usedModel = repair.model();
                last = repair.content();
                attempt = accept(repair.content(), mediaIds, null, null);
            }
            stage.accept("VALIDATING");
            if (attempt.document() == null) {
                throw new GenerationFailure(attempt.problems(), planNode, last, input, output, repairs, budget, usage(budget, stages, null));
            }
            DesignQuality.Result quality = DesignQuality.assess(attempt.document(), direction);
            if (quality.overall() < 72 && repairs == 0 && repairsAllowed > 0) {
                stage.accept("REPAIRING");
                OpenRouterClient.Completion refine = call(client, settings, model(settings.getRepairModel(), settings.getGenerationModel()), settings.getRepairFallbacks(), budget, PromptBuilder.refine(quality.issues(), attempt.document(), category, answers, planNode), PrismSchemas.document(mapper), PrismSchemas.DOCUMENT_SCHEMA);
                note(stages, "REFINING", refine);
                input += refine.inputTokens();
                output += refine.outputTokens();
                usedModel = refine.model();
                last = refine.content();
                Attempt refined = accept(refine.content(), mediaIds, null, null);
                if (refined.document() != null) {
                    attempt = refined;
                    repairs++;
                }
            }
            ObjectNode directed = TypographyApplicator.apply(
                    DesignApplicator.apply(
                            ReplyButtonEnricher.apply(CountdownEnricher.apply(attempt.document(), answers)),
                            direction
                    ),
                    planNode,
                    direction.id()
            );
            directed = ResponsiveDefaults.apply(directed);
            quality = DesignQuality.assess(directed, direction);
            ObjectNode report = usage(budget, stages, null);
            report.set("quality", quality.json(mapper));
            return new Generated(planNode, last, directed, attempt.warnings(), input, output, usedModel, repairs, budget.cost(), budget.latencyMs(), budget.requests(), report);
        } catch (OpenRouterException exception) {
            throw new GenerationFailure(exception.getMessage(), planNode, last, input, output, repairs, budget, usage(budget, stages, null));
        }
    }

    private OpenRouterClient.Completion call(OpenRouterClient client, PrismProperties.OpenRouter settings, String model, String fallbacks, AttemptBudget budget, String user, JsonNode schema, String schemaName) {
        OpenRouterClient.Route route = new OpenRouterClient.Route(
                model(model, ""),
                OpenRouterModels.split(fallbacks),
                settings.isAllowPaidFallback(),
                settings.getMaxRetries(),
                settings.getMaxFallbacks(),
                budget
        );
        return client.complete(route, PromptBuilder.SYSTEM, user, schema, schemaName);
    }

    private void note(com.fasterxml.jackson.databind.node.ArrayNode stages, String name, OpenRouterClient.Completion completion) {
        ObjectNode stage = stages.addObject();
        stage.put("stage", name);
        stage.put("requestedModel", completion.requestedModel());
        stage.put("actualModel", completion.model());
        stage.put("inputTokens", completion.inputTokens());
        stage.put("outputTokens", completion.outputTokens());
        if (completion.costUsd() != null) {
            stage.put("costUsd", completion.costUsd());
        }
        stage.put("latencyMs", completion.latencyMs());
        stage.put("structured", completion.structured());
        stage.put("status", "COMPLETED");
    }

    private ObjectNode usage(AttemptBudget budget, ArrayNode stages, JsonNode qa) {
        return usage(budget, stages, qa, mapper.createArrayNode(), "none");
    }

    private ObjectNode usage(AttemptBudget budget, ArrayNode stages, JsonNode qa, JsonNode retrieved, String retrievalMode) {
        ObjectNode usage = mapper.createObjectNode();
        usage.set("stages", stages);
        usage.put("costUsd", budget.cost());
        usage.put("latencyMs", budget.latencyMs());
        usage.put("requests", budget.requests());
        usage.put("retrievalMode", retrievalMode == null ? "none" : retrievalMode);
        usage.set("retrievedReferences", retrieved == null ? mapper.createArrayNode() : retrieved);
        if (qa != null) {
            usage.set("visualQa", qa);
        }
        return usage;
    }

    private ObjectNode directorToCreative(JsonNode director) {
        ObjectNode creative = mapper.createObjectNode();
        creative.put("archetypeId", director.path("archetypeId").asText(""));
        creative.put("rationale", director.path("rationale").asText(""));
        creative.put("motionStrategy", director.path("motionStrategy").asText("subtle"));
        creative.put("visualComplexity", director.path("visualComplexity").asText(director.path("designSystemHints").path("density").asText("balanced")));
        creative.set("selectedReferenceIds", director.path("selectedReferenceIds").isArray() ? director.path("selectedReferenceIds") : mapper.createArrayNode());
        if (director.path("designSystemHints").isObject()) {
            creative.set("designSystemHints", director.path("designSystemHints"));
        }
        return creative;
    }

    private ObjectNode directorToBlueprint(JsonNode director) {
        ObjectNode blueprint = mapper.createObjectNode();
        blueprint.put("archetypeId", director.path("archetypeId").asText(""));
        blueprint.put("pageIntent", director.path("pageIntent").asText(""));
        blueprint.set("hero", director.path("hero").isObject() ? director.path("hero") : mapper.createObjectNode());
        blueprint.set("sections", director.path("sections").isArray() ? director.path("sections") : mapper.createArrayNode());
        return blueprint;
    }

    private void applyDirectorDesignHints(ObjectNode designSystem, JsonNode creative) {
        JsonNode hints = creative.path("designSystemHints");
        if (!hints.isObject()) {
            return;
        }
        ObjectNode colors = designSystem.with("colors");
        if (hints.path("background").isTextual()) {
            colors.put("background", hints.path("background").asText());
        }
        if (hints.path("text").isTextual()) {
            colors.put("text", hints.path("text").asText());
        }
        if (hints.path("accent").isTextual()) {
            colors.put("accent", hints.path("accent").asText());
            colors.put("primary", hints.path("accent").asText());
        }
        ObjectNode fonts = designSystem.with("fonts");
        if (hints.path("displayFont").isTextual()) {
            fonts.put("display", hints.path("displayFont").asText());
            fonts.put("heading", hints.path("displayFont").asText());
        }
        if (hints.path("bodyFont").isTextual()) {
            fonts.put("body", hints.path("bodyFont").asText());
        }
    }

    private Attempt accept(JsonNode raw, Set<String> mediaIds, ObjectNode designSystem, JsonNode creative) {
        List<String> warnings = new ArrayList<>();
        try {
            String schemaProblem = PrismSchemaCheck.problem(PrismSchemas.document(mapper), raw);
            if (schemaProblem != null) {
                throw new IllegalArgumentException(schemaProblem);
            }
            ObjectNode normalized = normalizer.normalize(raw, mediaIds, warnings);
            if (designSystem != null) {
                normalized.set("designTokens", DesignSystemTokens.sanitize(mapper, designSystem));
            }
            if (creative != null && creative.isObject()) {
                ObjectNode creativeMeta = normalized.putObject("creative");
                creativeMeta.put("archetypeId", creative.path("archetypeId").asText(""));
                creativeMeta.put("rationale", creative.path("rationale").asText(""));
                creativeMeta.put("promptVersion", CreativeSchemas.PROMPT_VERSION);
                creativeMeta.put("motionStrategy", creative.path("motionStrategy").asText("subtle"));
                creativeMeta.put("visualComplexity", creative.path("visualComplexity").asText("balanced"));
            }
            ObjectNode clean = validator.sanitize(normalized);
            if (!hasCopy(clean.path("root"))) {
                throw new IllegalArgumentException("Headings and paragraphs need the words from the answers in props.text.");
            }
            return new Attempt(clean, warnings, "");
        } catch (ApiException | IllegalArgumentException exception) {
            return new Attempt(null, warnings, exception.getMessage());
        }
    }

    private boolean hasCopy(JsonNode node) {
        String type = node.path("type").asText("");
        if (("HEADING".equals(type) || "PARAGRAPH".equals(type) || "BUTTON".equals(type) || "QUOTE".equals(type)) && !node.path("props").path("text").asText("").isBlank()) {
            return true;
        }
        for (JsonNode child : node.path("children")) {
            if (hasCopy(child)) {
                return true;
            }
        }
        return false;
    }

    private String firstTitle(JsonNode answers) {
        for (String key : List.of("coupleNames", "person", "parents", "title", "businessName", "name", "organisation")) {
            String value = answers.path(key).asText("").trim();
            if (!value.isBlank()) {
                return value.length() > 160 ? value.substring(0, 160) : value;
            }
        }
        return "Untitled website";
    }

    private String model(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        throw new OpenRouterException(OpenRouterException.Kind.AUTH, "Website generation is not configured.");
    }

    public record Generated(JsonNode plan, JsonNode raw, ObjectNode document, List<String> warnings, int inputTokens, int outputTokens, String model, int repairs, BigDecimal costUsd, long latencyMs, int requests, JsonNode usage) {
    }

    public static class GenerationFailure extends RuntimeException {
        private final JsonNode plan;
        private final JsonNode raw;
        private final int inputTokens;
        private final int outputTokens;
        private final int repairs;
        private final BigDecimal costUsd;
        private final long latencyMs;
        private final int requests;
        private final JsonNode usage;

        public GenerationFailure(String message, JsonNode plan, JsonNode raw, int inputTokens, int outputTokens, int repairs, AttemptBudget budget, JsonNode usage) {
            super(message == null || message.isBlank() ? "The generated page did not pass validation." : message);
            this.plan = plan;
            this.raw = raw;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.repairs = repairs;
            this.costUsd = budget == null ? null : budget.cost();
            this.latencyMs = budget == null ? 0 : budget.latencyMs();
            this.requests = budget == null ? 0 : budget.requests();
            this.usage = usage;
        }

        public JsonNode plan() { return plan; }
        public JsonNode raw() { return raw; }
        public int inputTokens() { return inputTokens; }
        public int outputTokens() { return outputTokens; }
        public int repairs() { return repairs; }
        public BigDecimal costUsd() { return costUsd; }
        public long latencyMs() { return latencyMs; }
        public int requests() { return requests; }
        public JsonNode usage() { return usage; }
    }

    private record Attempt(ObjectNode document, List<String> warnings, String problems) {
    }
}

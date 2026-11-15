package com.prism.creation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.category.CategoryRepository;
import com.prism.category.EventCategory;
import com.prism.editor.EditorDocumentRepository;
import com.prism.editor.EditorDocumentService;
import com.prism.event.EventDeletionService;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.imagegen.ImageGenerationJobService;
import com.prism.media.MediaRepository;
import com.prism.revision.EventRevision;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Slugs;
import com.prism.template.EventTemplate;
import com.prism.template.TemplateRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CreationService {

    private static final Set<String> ACTIVE_JOBS = Set.of("QUEUED", "PLANNING", "GENERATING", "VALIDATING", "REPAIRING", "IMAGES");
    private static final List<String> TITLE_KEYS = List.of("coupleNames", "person", "parents", "title", "businessName", "name", "organisation");

    private final WebsiteCategoryRepository categories;
    private final QuestionnaireDefinitionRepository definitions;
    private final WebsiteCreationSessionRepository sessions;
    private final WebsiteGenerationJobRepository jobs;
    private final CategoryRepository eventCategories;
    private final TemplateRepository templates;
    private final EventService events;
    private final EventDeletionService deletion;
    private final EditorDocumentRepository documents;
    private final EditorDocumentService editor;
    private final MediaRepository media;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final AiRuntime runtime;
    private final ArchetypeCatalog archetypes;
    private final ImageGenerationJobService imageJobs;

    public CreationService(
            WebsiteCategoryRepository categories,
            QuestionnaireDefinitionRepository definitions,
            WebsiteCreationSessionRepository sessions,
            WebsiteGenerationJobRepository jobs,
            CategoryRepository eventCategories,
            TemplateRepository templates,
            EventService events,
            EventDeletionService deletion,
            EditorDocumentRepository documents,
            EditorDocumentService editor,
            MediaRepository media,
            ObjectMapper mapper,
            ApplicationEventPublisher publisher,
            AiRuntime runtime,
            ArchetypeCatalog archetypes,
            ImageGenerationJobService imageJobs
    ) {
        this.categories = categories;
        this.definitions = definitions;
        this.sessions = sessions;
        this.jobs = jobs;
        this.eventCategories = eventCategories;
        this.templates = templates;
        this.events = events;
        this.deletion = deletion;
        this.documents = documents;
        this.editor = editor;
        this.media = media;
        this.mapper = mapper;
        this.publisher = publisher;
        this.runtime = runtime;
        this.imageJobs = imageJobs;
        this.archetypes = archetypes;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> categories() {
        return categories.findByActiveTrueOrderByDisplayOrderAsc().stream().map(this::category).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> category(String code) {
        return category(requireCategory(code));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> questionnaire(String code, Integer version) {
        WebsiteCategory category = requireCategory(code);
        QuestionnaireDefinition definition = version == null
                ? activeDefinition(category)
                : definitions.findByCategoryIdAndVersion(category.id, version).orElseThrow(() -> ApiException.notFound("That questionnaire was not found."));
        return questionnaire(category, definition);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sessions(UserPrincipal principal) {
        return sessions.findByUserIdOrderByUpdatedAtDesc(principal.id()).stream().map(session -> session(session, false)).toList();
    }

    @Transactional
    public Map<String, Object> start(UserPrincipal principal, String code, String ip) {
        WebsiteCategory category = requireCategory(code);
        if (!category.active) {
            throw ApiException.badRequest("That website type is not available.");
        }
        QuestionnaireDefinition definition = activeDefinition(category);
        EventCategory eventCategory = eventCategories.findByCode(category.eventCategoryCode)
                .filter(item -> item.active)
                .orElseThrow(() -> ApiException.badRequest("That website type is not available yet."));
        EventTemplate template = templates.findByCategoryIdAndActiveTrueOrderByNameAsc(eventCategory.id).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("That website type is not available yet."));
        Map<String, Object> created = events.create(principal, eventCategory.id, template.id, "Untitled website", freshSlug(), 7, ip);
        WebsiteCreationSession session = new WebsiteCreationSession();
        session.userId = principal.id();
        session.categoryId = category.id;
        session.questionnaireVersion = definition.version;
        session.eventId = (UUID) created.get("id");
        session.answersJson = mapper.createObjectNode();
        session.currentStep = "questions";
        session.status = "IN_PROGRESS";
        sessions.save(session);
        return session(session, true);
    }

    @Transactional
    public void discard(UUID id, UserPrincipal principal, String ip) {
        WebsiteCreationSession session = owned(id, principal);
        if (session.eventId != null) {
            deletion.delete(events.requireDeletable(session.eventId, principal), principal, ip);
            return;
        }
        Instant now = Instant.now();
        UUID sessionId = session.id;
        jobs.cancelOpenForSession(sessionId, now);
        jobs.detachSession(sessionId);
        sessions.deleteById(sessionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> session(UUID id, UserPrincipal principal) {
        return session(owned(id, principal), true);
    }

    @Transactional
    public Map<String, Object> saveAnswers(UUID id, JsonNode answers, String step, UserPrincipal principal) {
        WebsiteCreationSession session = owned(id, principal);
        rejectFinished(session);
        QuestionnaireDefinition definition = pinned(session);
        session.answersJson = QuestionnaireCatalog.keepAnswers(mapper, effectiveConfiguration(categoryCode(session), definition.configurationJson), answers);
        if (step != null && !step.isBlank()) {
            session.currentStep = step(step);
        }
        if ("ANSWERS_COMPLETED".equals(session.status) || "GENERATED".equals(session.status) || "GENERATION_FAILED".equals(session.status)) {
            session.status = "IN_PROGRESS";
        }
        session.updatedAt = Instant.now();
        return session(sessions.save(session), true);
    }

    @Transactional
    public Map<String, Object> progress(UUID id, String step, UserPrincipal principal) {
        WebsiteCreationSession session = owned(id, principal);
        session.currentStep = step(step);
        session.updatedAt = Instant.now();
        return session(sessions.save(session), true);
    }

    @Transactional
    public Map<String, Object> complete(UUID id, UserPrincipal principal) {
        WebsiteCreationSession session = owned(id, principal);
        rejectFinished(session);
        QuestionnaireDefinition definition = pinned(session);
        QuestionnaireCatalog.requireVisible(effectiveConfiguration(categoryCode(session), definition.configurationJson), session.answersJson);
        session.status = "ANSWERS_COMPLETED";
        session.currentStep = "review";
        session.updatedAt = Instant.now();
        return session(sessions.save(session), true);
    }

    @Transactional
    public Map<String, Object> generate(UUID id, boolean confirmReplace, UserPrincipal principal) {
        WebsiteCreationSession session = owned(id, principal);
        if (session.eventId == null) {
            throw ApiException.badRequest("This website has no draft yet.");
        }
        EventSite event = events.requireOwner(session.eventId, principal);
        if (event.publishedRevisionId != null || "PUBLISHED".equals(event.status) || "SUSPENDED".equals(event.status)) {
            throw ApiException.badRequest("Start a new website. A published site is left unchanged.");
        }
        EventRevision revision = events.latest(event.id);
        if (!"DRAFT".equals(revision.submissionStatus)) {
            throw ApiException.badRequest("Save a draft before generating a page.");
        }
        boolean existing = documents.findByRevisionId(revision.id).isPresent();
        if (existing && !confirmReplace) {
            throw ApiException.conflict("A draft page already exists. Confirm to replace that draft.");
        }
        var running = jobs.findFirstBySessionIdAndStatusInOrderByCreatedAtDesc(session.id, ACTIVE_JOBS);
        if (running.isPresent()) {
            return job(running.get());
        }
        PrismProperties.OpenRouter modelSettings = runtime.forThisRun();
        runtime.assertDailyBudget(modelSettings);
        if (jobs.countByUserId(principal.id()) >= runtime.generationLimit(modelSettings)) {
            throw ApiException.tooMany("You have reached the generation limit.");
        }
        QuestionnaireDefinition definition = pinned(session);
        QuestionnaireCatalog.requireVisible(definition.configurationJson, session.answersJson);
        WebsiteGenerationJob job = new WebsiteGenerationJob();
        job.sessionId = session.id;
        job.userId = principal.id();
        job.eventId = event.id;
        job.status = "QUEUED";
        job.promptVersion = CreativeSchemas.PROMPT_VERSION;
        job.inputTokens = 0;
        job.outputTokens = 0;
        jobs.save(job);
        session.status = "GENERATION_PENDING";
        session.currentStep = "generating";
        session.updatedAt = Instant.now();
        sessions.save(session);
        publisher.publishEvent(new GenerationRequested(job.id));
        return job(job);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> job(UUID id, UserPrincipal principal) {
        return job(ownedJob(id, principal));
    }

    @Transactional
    public Map<String, Object> cancel(UUID id, UserPrincipal principal) {
        WebsiteGenerationJob job = ownedJob(id, principal);
        if ("COMPLETED".equals(job.status) || "FAILED".equals(job.status) || "CANCELLED".equals(job.status)) {
            return job(job);
        }
        job.status = "CANCELLED";
        job.completedAt = Instant.now();
        job.errorMessage = "Cancelled.";
        WebsiteCreationSession session = sessions.findById(job.sessionId).orElse(null);
        if (session != null && principal.id().equals(session.userId)) {
            session.status = "ANSWERS_COMPLETED";
            session.currentStep = "review";
            session.updatedAt = Instant.now();
        }
        return job(jobs.save(job));
    }

    @Transactional
    public Map<String, Object> retry(UUID id, UserPrincipal principal) {
        WebsiteGenerationJob previous = ownedJob(id, principal);
        if (!"FAILED".equals(previous.status) && !"CANCELLED".equals(previous.status)) {
            throw ApiException.badRequest("Only a failed generation can be retried.");
        }
        return generate(previous.sessionId, true, principal);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> result(UUID id, UserPrincipal principal) {
        WebsiteGenerationJob job = ownedJob(id, principal);
        if (!"COMPLETED".equals(job.status)) {
            throw ApiException.badRequest("This generation is not ready.");
        }
        Map<String, Object> body = job(job);
        body.put("document", job.generatedDocumentJson);
        return body;
    }

    @Transactional
    public Map<String, Object> openEditor(UUID id, UserPrincipal principal) {
        WebsiteCreationSession session = owned(id, principal);
        if (session.eventId == null || !"GENERATED".equals(session.status) && !"EDITING".equals(session.status)) {
            throw ApiException.badRequest("Generate a page before opening the editor.");
        }
        session.status = "EDITING";
        session.currentStep = "editor";
        session.updatedAt = Instant.now();
        sessions.save(session);
        return Map.of("eventId", session.eventId, "path", "/app/events/" + session.eventId + "/studio");
    }

    @Transactional(readOnly = true)
    public Prepared prepare(UUID jobId) {
        WebsiteGenerationJob job = jobs.findById(jobId).orElse(null);
        if (job == null || "CANCELLED".equals(job.status) || "COMPLETED".equals(job.status) || "FAILED".equals(job.status)) {
            return null;
        }
        WebsiteCreationSession session = sessions.findById(job.sessionId).orElseThrow();
        WebsiteCategory category = categories.findById(session.categoryId).orElseThrow();
        QuestionnaireDefinition definition = pinned(session);
        JsonNode configuration = effectiveConfiguration(category.code, definition.configurationJson);
        ObjectNode answers = QuestionnaireCatalog.visibleAnswers(mapper, configuration, session.answersJson);
        injectEventPalette(answers, session.eventId);
        Set<String> ownedMedia = ownedMedia(configuration, session.answersJson, session.eventId);
        return new Prepared(job.id, session.id, session.userId, session.eventId, category.code, answers, ownedMedia);
    }

    /** Prefer answers.colourPalette; fall back to the event theme saved by DraftSide "Use this palette". */
    private void injectEventPalette(ObjectNode answers, UUID eventId) {
        if (answers == null || eventId == null || DesignDirection.hasUserPalette(answers)) {
            return;
        }
        EventRevision revision = events.latest(eventId);
        if (revision == null || revision.configurationJson == null) {
            return;
        }
        JsonNode theme = revision.configurationJson.path("theme");
        if (!theme.isObject()) {
            return;
        }
        ObjectNode palette = mapper.createObjectNode();
        palette.put("background", theme.path("background").asText(""));
        palette.put("ink", theme.path("ink").asText(""));
        palette.put("accent", theme.path("accent").asText(""));
        QuestionnaireCatalog.copyUserPalette(mapper, answers, mapper.createObjectNode().set("colourPalette", palette));
    }

    @Transactional
    public void advance(UUID jobId, String status) {
        WebsiteGenerationJob current = jobs.findById(jobId).orElseThrow();
        if ("CANCELLED".equals(current.status)) {
            return;
        }
        current.status = status;
        if (current.startedAt == null) {
            current.startedAt = Instant.now();
        }
        WebsiteCreationSession session = sessions.findById(current.sessionId).orElse(null);
        if (session != null) {
            session.status = "GENERATING";
            session.updatedAt = Instant.now();
        }
    }

    @Transactional
    public void rememberPrompt(UUID jobId, String prompt) {
        jobs.findById(jobId).ifPresent(job -> job.promptSnapshot = prompt);
    }

    @Transactional
    public void finish(UUID jobId, WebsiteDocumentGenerator.Generated generated, UUID sessionId) {
        WebsiteGenerationJob job = jobs.findById(jobId).orElseThrow();
        if ("CANCELLED".equals(job.status)) {
            return;
        }
        job.status = "COMPLETED";
        job.model = generated.model();
        job.planningResponseJson = generated.plan();
        job.generationResponseJson = generated.raw();
        job.generatedDocumentJson = generated.document();
        job.inputTokens = generated.inputTokens();
        job.outputTokens = generated.outputTokens();
        job.costUsd = generated.costUsd();
        job.latencyMs = generated.latencyMs();
        job.requestCount = generated.requests();
        job.usageJson = generated.usage();
        job.completedAt = Instant.now();
        job.errorMessage = generated.warnings().isEmpty() ? null : String.join(" ", generated.warnings());
        WebsiteCreationSession session = sessions.findById(sessionId).orElseThrow();
        session.status = "GENERATED";
        session.currentStep = "preview";
        session.updatedAt = Instant.now();
    }

    @Transactional
    public void fail(UUID jobId, String message, JsonNode plan, JsonNode raw, int input, int output) {
        fail(jobId, message, plan, raw, input, output, null, 0L, 0, null);
    }

    @Transactional
    public void fail(UUID jobId, String message, JsonNode plan, JsonNode raw, int input, int output, java.math.BigDecimal cost, long latencyMs, int requests, JsonNode usage) {
        WebsiteGenerationJob job = jobs.findById(jobId).orElse(null);
        if (job == null || "CANCELLED".equals(job.status) || "COMPLETED".equals(job.status)) {
            return;
        }
        job.status = "FAILED";
        job.errorMessage = message == null ? "The website could not be generated." : message;
        job.planningResponseJson = plan;
        job.generationResponseJson = raw;
        job.inputTokens = input;
        job.outputTokens = output;
        job.costUsd = cost;
        job.latencyMs = latencyMs;
        job.requestCount = requests;
        job.usageJson = usage;
        job.completedAt = Instant.now();
        sessions.findById(job.sessionId).ifPresent(session -> {
            session.status = "GENERATION_FAILED";
            session.currentStep = "review";
            session.updatedAt = Instant.now();
        });
    }

    @Transactional
    public List<Map<String, Object>> adminCategories() {
        return categories.findAllByOrderByDisplayOrderAsc().stream().map(this::category).toList();
    }

    @Transactional
    public Map<String, Object> setCategoryActive(String code, boolean active) {
        WebsiteCategory category = requireCategory(code);
        category.active = active;
        category.updatedAt = Instant.now();
        return category(categories.save(category));
    }

    @Transactional
    public Map<String, Object> publishQuestionnaire(String code, JsonNode configuration) {
        if (configuration == null || !configuration.path("questions").isArray() || configuration.path("questions").isEmpty()) {
            throw ApiException.badRequest("A questionnaire needs at least one question.");
        }
        WebsiteCategory category = requireCategory(code);
        QuestionnaireDefinition latest = definitions.findFirstByCategoryIdOrderByVersionDesc(category.id)
                .orElseThrow(() -> ApiException.notFound("That questionnaire was not found."));
        definitions.findByCategoryIdAndActiveTrue(category.id).ifPresent(current -> {
            current.active = false;
            current.updatedAt = Instant.now();
        });
        QuestionnaireDefinition created = new QuestionnaireDefinition();
        created.categoryId = category.id;
        created.version = latest.version + 1;
        created.name = configuration.path("name").asText(category.name);
        created.description = configuration.path("description").asText(category.description);
        created.configurationJson = configuration;
        created.active = true;
        definitions.save(created);
        return questionnaire(category, created);
    }

    @Transactional
    public void installGenerated(UUID sessionId, JsonNode document, JsonNode plan) {
        WebsiteCreationSession session = sessions.findById(sessionId).orElseThrow();
        EventSite event = events.requireOwner(session.eventId, new UserPrincipal(session.userId, "", "", List.of("CUSTOMER")));
        EventRevision revision = events.latest(event.id);
        if (!"DRAFT".equals(revision.submissionStatus) || event.publishedRevisionId != null) {
            throw ApiException.badRequest("The draft changed before generation finished.");
        }
        editor.install(event, revision, document, session.userId);
        syncEventStart(event, revision, session);
        syncRsvp(event, revision, session);
        rename(event, session, plan, session.userId);
    }

    private void syncEventStart(EventSite event, EventRevision revision, WebsiteCreationSession session) {
        String iso = CountdownEnricher.isoFromAnswers(session.answersJson);
        if (iso == null || iso.isBlank() || revision.configurationJson == null || !revision.configurationJson.isObject()) {
            return;
        }
        ObjectNode configuration = revision.configurationJson.deepCopy();
        configuration.with("event").put("startAt", iso);
        revision.configurationJson = configuration;
    }

    private void syncRsvp(EventSite event, EventRevision revision, WebsiteCreationSession session) {
        if (revision.configurationJson == null || !revision.configurationJson.isObject()) {
            return;
        }
        if (!wantsRsvp(session.answersJson)) {
            return;
        }
        ObjectNode configuration = revision.configurationJson.deepCopy();
        configuration.with("rsvp").put("enabled", true);
        revision.configurationJson = configuration;
    }

    private static boolean wantsRsvp(JsonNode answers) {
        if (answers == null || !answers.isObject()) {
            return false;
        }
        return isYes(answers.path("rsvp").asText("")) || isYes(answers.path("simpleRsvp").asText(""));
    }

    private static boolean isYes(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "yes".equals(normalized) || "true".equals(normalized) || "1".equals(normalized);
    }

    private void rename(EventSite event, WebsiteCreationSession session, JsonNode plan, UUID userId) {
        if (!"Untitled website".equals(event.eventName)) {
            return;
        }
        String title = plan.path("title").asText("");
        if (title.isBlank()) {
            for (String key : TITLE_KEYS) {
                title = session.answersJson.path(key).asText("");
                if (!title.isBlank()) {
                    break;
                }
            }
        }
        if (title.isBlank()) {
            return;
        }
        String name = title.length() > 160 ? title.substring(0, 160) : title;
        String slug = null;
        try {
            String candidate = Slugs.normalize(name);
            if (events.availability(candidate, event.id).get("available") == Boolean.TRUE) {
                slug = candidate;
            }
        } catch (ApiException ignored) {
            slug = null;
        }
        events.update(event, name, slug, null, new UserPrincipal(userId, "", "", List.of("CUSTOMER")), "generation");
    }

    private Set<String> ownedMedia(JsonNode definition, JsonNode answers, UUID eventId) {
        Set<String> owned = new java.util.HashSet<>();
        for (String id : QuestionnaireCatalog.mediaIds(definition, answers)) {
            media.findByIdAndEventId(UUID.fromString(id), eventId).ifPresent(row -> owned.add(id));
        }
        return owned;
    }

    @Transactional(readOnly = true)
    public boolean cancelled(UUID jobId) {
        return jobs.findById(jobId).map(job -> "CANCELLED".equals(job.status)).orElse(true);
    }

    private WebsiteCategory requireCategory(String code) {
        return categories.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("That website type was not found."));
    }

    private QuestionnaireDefinition activeDefinition(WebsiteCategory category) {
        return definitions.findByCategoryIdAndActiveTrue(category.id)
                .orElseThrow(() -> ApiException.notFound("That questionnaire was not found."));
    }

    private QuestionnaireDefinition pinned(WebsiteCreationSession session) {
        return definitions.findByCategoryIdAndVersion(session.categoryId, session.questionnaireVersion)
                .orElseThrow(() -> ApiException.notFound("That questionnaire was not found."));
    }

    private WebsiteCreationSession owned(UUID id, UserPrincipal principal) {
        WebsiteCreationSession session = sessions.findById(id).orElseThrow(() -> ApiException.notFound("That website draft was not found."));
        if (!session.userId.equals(principal.id())) {
            throw ApiException.notFound("That website draft was not found.");
        }
        return session;
    }

    private WebsiteGenerationJob ownedJob(UUID id, UserPrincipal principal) {
        WebsiteGenerationJob job = jobs.findById(id).orElseThrow(() -> ApiException.notFound("That generation was not found."));
        if (!job.userId.equals(principal.id())) {
            throw ApiException.notFound("That generation was not found.");
        }
        return job;
    }

    private void rejectFinished(WebsiteCreationSession session) {
        if ("SUBMITTED".equals(session.status)) {
            throw ApiException.badRequest("This website has already been submitted.");
        }
    }

    private String step(String step) {
        return switch (step) {
            case "questions", "details", "review", "generating", "preview", "editor" -> step;
            default -> "questions";
        };
    }

    private String freshSlug() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String slug = "draft-" + UUID.randomUUID().toString().substring(0, 8);
            if (events.availability(slug, null).get("available") == Boolean.TRUE) {
                return slug;
            }
        }
        return "draft-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> category(WebsiteCategory category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", category.code);
        body.put("name", category.name);
        body.put("description", category.description);
        body.put("icon", category.icon);
        body.put("active", category.active);
        body.put("displayOrder", category.displayOrder);
        return body;
    }

    private Map<String, Object> questionnaire(WebsiteCategory category, QuestionnaireDefinition definition) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryCode", category.code);
        body.put("version", definition.version);
        body.put("name", definition.name);
        body.put("description", definition.description);
        body.put("questions", withCreativePreferences(category.code, definition.configurationJson.path("questions")));
        return body;
    }

    private String categoryCode(WebsiteCreationSession session) {
        return categories.findById(session.categoryId).map(category -> category.code).orElse("");
    }

    private JsonNode effectiveConfiguration(String categoryCode, JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            return configuration;
        }
        ObjectNode copy = configuration.deepCopy();
        copy.set("questions", withCreativePreferences(categoryCode, configuration.path("questions")));
        return copy;
    }

    private JsonNode withCreativePreferences(String categoryCode, JsonNode questions) {
        if (questions == null || !questions.isArray()) {
            return questions;
        }
        com.fasterxml.jackson.databind.node.ArrayNode out = mapper.createArrayNode();
        questions.forEach(out::add);
        boolean hasVisual = false;
        for (JsonNode question : questions) {
            if ("visualDirection".equals(question.path("key").asText())) {
                hasVisual = true;
                break;
            }
        }
        if (hasVisual) {
            return out;
        }
        ObjectNode direction = out.addObject();
        direction.put("key", "visualDirection");
        direction.put("type", "single");
        direction.put("group", "Design preferences");
        direction.put("label", "How should the page feel?");
        direction.put("required", false);
        direction.put("help", "Leave on Let WebAux choose, or pick a look. You can still edit everything after generation.");
        var options = direction.putArray("options");
        options.addObject().put("value", "ai").put("label", "Let WebAux choose");
        for (JsonNode archetype : archetypes.forCategory(categoryCode)) {
            options.addObject().put("value", archetype.path("id").asText()).put("label", archetype.path("name").asText());
        }
        ObjectNode richness = out.addObject();
        richness.put("key", "visualRichness");
        richness.put("type", "single");
        richness.put("group", "Design preferences");
        richness.put("label", "How rich should the visuals be?");
        richness.put("required", false);
        var richOptions = richness.putArray("options");
        richOptions.addObject().put("value", "minimal").put("label", "Minimal");
        richOptions.addObject().put("value", "balanced").put("label", "Balanced");
        richOptions.addObject().put("value", "expressive").put("label", "Expressive");
        ObjectNode motion = out.addObject();
        motion.put("key", "motionPreference");
        motion.put("type", "single");
        motion.put("group", "Design preferences");
        motion.put("label", "Motion preference");
        motion.put("required", false);
        var motionOptions = motion.putArray("options");
        motionOptions.addObject().put("value", "subtle").put("label", "Subtle");
        motionOptions.addObject().put("value", "cinematic").put("label", "Cinematic");
        motionOptions.addObject().put("value", "playful").put("label", "Playful");
        return out;
    }

    private Map<String, Object> session(WebsiteCreationSession session, boolean includeAnswers) {
        WebsiteCategory category = categories.findById(session.categoryId).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", session.id);
        body.put("categoryCode", category == null ? null : category.code);
        body.put("categoryName", category == null ? null : category.name);
        body.put("questionnaireVersion", session.questionnaireVersion);
        body.put("eventId", session.eventId);
        body.put("status", session.status);
        body.put("currentStep", session.currentStep);
        body.put("updatedAt", session.updatedAt);
        if (includeAnswers) {
            body.put("answers", session.answersJson);
            body.put("questions", withCreativePreferences(category == null ? "" : category.code, pinned(session).configurationJson.path("questions")));
        }
        boolean hasDocument = false;
        if (session.eventId != null) {
            EventRevision revision = events.latest(session.eventId);
            hasDocument = documents.findByRevisionId(revision.id).isPresent();
        }
        body.put("hasDocument", hasDocument);
        jobs.findFirstBySessionIdOrderByCreatedAtDesc(session.id).ifPresent(job -> {
            body.put("latestJobId", job.id);
            body.put("latestJobStatus", job.status);
        });
        return body;
    }

    public record Prepared(UUID jobId, UUID sessionId, UUID userId, UUID eventId, String category, ObjectNode answers, Set<String> mediaIds) {
    }

    private Map<String, Object> job(WebsiteGenerationJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", job.id);
        body.put("sessionId", job.sessionId);
        body.put("eventId", job.eventId);
        body.put("status", job.status);
        body.put("errorMessage", job.errorMessage);
        body.put("model", job.model);
        body.put("inputTokens", job.inputTokens);
        body.put("outputTokens", job.outputTokens);
        body.put("costUsd", job.costUsd);
        body.put("latencyMs", job.latencyMs);
        body.put("requestCount", job.requestCount);
        body.put("startedAt", job.startedAt);
        body.put("completedAt", job.completedAt);
        body.put("imageProgress", imageJobs.progressForWebsiteJob(job.id));
        return body;
    }
}

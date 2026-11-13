package com.prism.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.category.CategoryRepository;
import com.prism.category.EventCategory;
import com.prism.editor.EditorDocument;
import com.prism.editor.EditorDocumentRepository;
import com.prism.hosting.HostingPeriod;
import com.prism.hosting.HostingService;
import com.prism.media.MediaRepository;
import com.prism.notification.OutboxService;
import com.prism.pricing.PricingService;
import com.prism.review.EventReview;
import com.prism.review.ReviewRepository;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.audit.AuditService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Slugs;
import com.prism.template.EventTemplate;
import com.prism.template.TemplateRepository;
import com.prism.template.TemplateVersion;
import com.prism.template.TemplateVersionRepository;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository events;
    private final RevisionRepository revisions;
    private final CategoryRepository categories;
    private final TemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final UrlReservationRepository reservations;
    private final MediaRepository media;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ConfigurationSanitizer sanitizer;
    private final HostingService hosting;
    private final PricingService pricing;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final PrismProperties properties;
    private final EditorDocumentRepository editorDocuments;

    public EventService(
            EventRepository events,
            RevisionRepository revisions,
            CategoryRepository categories,
            TemplateRepository templates,
            TemplateVersionRepository versions,
            UrlReservationRepository reservations,
            MediaRepository media,
            ReviewRepository reviews,
            UserRepository users,
            ConfigurationSanitizer sanitizer,
            HostingService hosting,
            PricingService pricing,
            OutboxService outbox,
            AuditService audit,
            ObjectMapper mapper,
            PrismProperties properties,
            EditorDocumentRepository editorDocuments
    ) {
        this.events = events;
        this.revisions = revisions;
        this.categories = categories;
        this.templates = templates;
        this.versions = versions;
        this.reservations = reservations;
        this.media = media;
        this.reviews = reviews;
        this.users = users;
        this.sanitizer = sanitizer;
        this.hosting = hosting;
        this.pricing = pricing;
        this.outbox = outbox;
        this.audit = audit;
        this.mapper = mapper;
        this.properties = properties;
        this.editorDocuments = editorDocuments;
    }

    public EventSite requireOwner(UUID id, UserPrincipal principal) {
        EventSite event = events.findById(id).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        if (!event.userId.equals(principal.id())) {
            throw ApiException.notFound("That event site was not found.");
        }
        return event;
    }

    public EventSite requireDeletable(UUID id, UserPrincipal principal) {
        EventSite event = events.findById(id).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        if (event.userId.equals(principal.id()) || principal.hasRole("ADMIN")) {
            return event;
        }
        if (principal.isStaff()) {
            throw ApiException.forbidden("You do not have access to this.");
        }
        throw ApiException.notFound("That event site was not found.");
    }

    public EventSite requireReadable(UUID id, UserPrincipal principal) {
        EventSite event = events.findById(id).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        if (!event.userId.equals(principal.id()) && !principal.isStaff()) {
            throw ApiException.notFound("That event site was not found.");
        }
        return event;
    }

    @Transactional
    public Map<String, Object> create(UserPrincipal principal, UUID categoryId, UUID templateId, String eventName, String rawSlug, int days, String ip) {
        if (eventName == null || eventName.isBlank() || eventName.trim().length() > 160) {
            throw ApiException.badRequest("Enter an event name.");
        }
        EventCategory category = categories.findById(categoryId)
                .filter(item -> item.active)
                .orElseThrow(() -> ApiException.badRequest("Choose an active category."));
        EventTemplate template = templates.findById(templateId)
                .filter(item -> item.active && item.categoryId.equals(category.id))
                .orElseThrow(() -> ApiException.badRequest("Choose an active template in that category."));
        TemplateVersion version = versions.findFirstByTemplateIdAndActiveTrueOrderByVersionNumberDesc(template.id)
                .orElseThrow(() -> ApiException.badRequest("That template has no active version."));
        String slug = Slugs.normalize(rawSlug);
        if (events.existsBySlug(slug) || reservations.existsBySlug(slug)) {
            throw ApiException.conflict("That address is already taken.");
        }
        pricing.quote(days);
        EventSite event = new EventSite();
        event.userId = principal.id();
        event.categoryId = category.id;
        event.templateVersionId = version.id;
        event.eventName = eventName.trim();
        event.slug = slug;
        event.status = "DRAFT";
        event.requestedDays = days;
        try {
            events.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("That address is already taken.");
        }
        ObjectNode configuration = defaults(version, event.eventName);
        EventRevision revision = new EventRevision();
        revision.eventId = event.id;
        revision.revisionNumber = 1;
        revision.configurationJson = configuration;
        revision.submissionStatus = "DRAFT";
        revisions.save(revision);
        UrlReservation reservation = new UrlReservation();
        reservation.slug = slug;
        reservation.eventId = event.id;
        reservation.userId = principal.id();
        reservations.save(reservation);
        audit.record(principal.id(), "EVENT_CREATED", "EVENT", event.id, Map.of("slug", slug, "template", template.code), ip);
        return present(event, revision);
    }

    @Transactional
    public Map<String, Object> update(EventSite event, String eventName, String rawSlug, Integer days, UserPrincipal principal, String ip) {
        if (eventName != null && !eventName.isBlank()) {
            if (eventName.trim().length() > 160) {
                throw ApiException.badRequest("The event name is too long.");
            }
            event.eventName = eventName.trim();
        }
        if (days != null) {
            pricing.quote(days);
            if (event.publishedRevisionId != null && !"APPROVED".equals(event.status) && !"EXPIRED".equals(event.status) && !"DRAFT".equals(event.status) && !"CHANGES_REQUESTED".equals(event.status)) {
                // Hosting length for a live site changes through an extension payment.
            }
            if ("PUBLISHED".equals(event.status) || "SUSPENDED".equals(event.status)) {
                throw ApiException.badRequest("Change hosting length from the extension page once a site is live.");
            }
            event.requestedDays = days;
        }
        if (rawSlug != null && !rawSlug.isBlank()) {
            if (event.publishedRevisionId != null) {
                throw ApiException.badRequest("The public address stays fixed once a site has been published.");
            }
            String slug = Slugs.normalize(rawSlug);
            if (!slug.equals(event.slug)) {
                if (events.existsBySlug(slug) || reservations.findBySlug(slug).filter(row -> !event.id.equals(row.eventId)).isPresent()) {
                    throw ApiException.conflict("That address is already taken.");
                }
                reservations.findBySlug(event.slug).ifPresent(row -> row.slug = slug);
                event.slug = slug;
            }
        }
        events.save(event);
        return present(event, latest(event.id));
    }

    @Transactional
    public Map<String, Object> saveConfiguration(EventSite event, JsonNode configuration, UserPrincipal principal) {
        ensureNoQueue(event.id);
        ObjectNode clean = sanitizer.sanitize(configuration, false);
        if (!clean.path("event").path("name").asText("").isBlank()) {
            event.eventName = clean.path("event").path("name").asText();
        }
        EventRevision revision = editable(event, clean);
        events.save(event);
        return present(event, revision);
    }

    @Transactional
    public Map<String, Object> submit(EventSite event, UserPrincipal principal, String ip) {
        ensureNoQueue(event.id);
        EventRevision latest = latest(event.id);
        if ("CHANGES_REQUESTED".equals(latest.submissionStatus) || "REJECTED".equals(latest.submissionStatus) || "APPROVED".equals(latest.submissionStatus)) {
            latest = cloneRevision(event, latest, latest.configurationJson);
        }
        if (!"DRAFT".equals(latest.submissionStatus)) {
            throw ApiException.badRequest("Save a draft before submitting.");
        }
        ObjectNode strict = sanitizer.sanitize(latest.configurationJson, true);
        assertMedia(event.id, strict);
        latest.configurationJson = strict;
        latest.submissionStatus = "SUBMITTED";
        latest.submittedAt = Instant.now();
        revisions.save(latest);
        event.eventName = strict.path("event").path("name").asText(event.eventName);
        if (!"PUBLISHED".equals(event.status) && !"SUSPENDED".equals(event.status)) {
            event.status = "SUBMITTED";
        }
        events.save(event);
        UserAccount owner = users.findById(event.userId).orElse(null);
        outbox.enqueue("EVENT", event.id, "SUBMISSION_CONFIRMATION", Map.of(
                "phone", owner == null ? "" : owner.phoneNumber,
                "name", owner == null ? "" : owner.fullName,
                "eventName", event.eventName,
                "slug", event.slug
        ), "submitted:" + latest.id);
        audit.record(principal.id(), "EVENT_SUBMITTED", "EVENT", event.id, Map.of("revision", latest.revisionNumber), ip);
        return present(event, latest);
    }

    public Map<String, Object> present(EventSite event, EventRevision revision) {
        EventCategory category = categories.findById(event.categoryId).orElse(null);
        TemplateVersion version = versions.findById(event.templateVersionId).orElse(null);
        EventTemplate template = version == null ? null : templates.findById(version.templateId).orElse(null);
        HostingPeriod open = hosting.latestOpen(event.id);
        Instant now = Instant.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", event.id);
        body.put("eventName", event.eventName);
        body.put("slug", event.slug);
        body.put("status", event.status);
        body.put("requestedDays", event.requestedDays);
        body.put("categoryId", event.categoryId);
        body.put("categoryName", category == null ? null : category.name);
        body.put("templateId", template == null ? null : template.id);
        body.put("templateName", template == null ? null : template.name);
        body.put("templateCode", template == null ? null : template.code);
        body.put("templateVersionId", event.templateVersionId);
        body.put("templateVersionNumber", version == null ? null : version.versionNumber);
        body.put("revisionId", revision == null ? null : revision.id);
        body.put("revisionNumber", revision == null ? null : revision.revisionNumber);
        body.put("submissionStatus", revision == null ? null : revision.submissionStatus);
        body.put("configuration", revision == null ? null : revision.configurationJson);
        body.put("editorDocument", revision == null ? null : editorDocuments.findByRevisionId(revision.id).map(document -> document.documentJson).orElse(null));
        body.put("approvedRevisionId", event.approvedRevisionId);
        body.put("publishedRevisionId", event.publishedRevisionId);
        body.put("createdAt", event.createdAt);
        body.put("updatedAt", event.updatedAt);
        body.put("publicUrl", properties.getPublicBaseUrl().replaceAll("/$", "") + "/" + event.slug);
        Map<String, Object> hostingView = new LinkedHashMap<>();
        if (open == null) {
            hostingView.put("status", null);
            hostingView.put("startAt", null);
            hostingView.put("endAt", null);
            hostingView.put("days", null);
            hostingView.put("daysRemaining", 0);
        } else {
            hostingView.put("status", open.status);
            hostingView.put("startAt", open.startAt);
            hostingView.put("endAt", open.endAt);
            hostingView.put("days", open.days);
            hostingView.put("daysRemaining", hosting.daysRemaining(event.id, now));
            hostingView.put("dailyRatePaise", open.dailyRatePaise);
        }
        body.put("hosting", hostingView);
        List<EventReview> history = reviews.findByEventIdOrderByCreatedAtDesc(event.id);
        body.put("latestReview", history.isEmpty() ? null : reviewView(history.getFirst()));
        return body;
    }

    public Map<String, Object> availability(String rawSlug, UUID ignoreEventId) {
        String slug = Slugs.normalize(rawSlug);
        boolean taken = events.findBySlug(slug).filter(event -> ignoreEventId == null || !event.id.equals(ignoreEventId)).isPresent()
                || reservations.findBySlug(slug).filter(row -> ignoreEventId == null || !ignoreEventId.equals(row.eventId)).isPresent();
        return Map.of("slug", slug, "available", !taken);
    }

    public EventRevision latest(UUID eventId) {
        return revisions.findFirstByEventIdOrderByRevisionNumberDesc(eventId)
                .orElseThrow(() -> ApiException.notFound("That event site has no revision."));
    }

    public EventRevision requireRevision(UUID eventId, UUID revisionId) {
        EventRevision revision = revisions.findById(revisionId).orElseThrow(() -> ApiException.notFound("That revision was not found."));
        if (!revision.eventId.equals(eventId)) {
            throw ApiException.notFound("That revision was not found.");
        }
        return revision;
    }

    private EventRevision editable(EventSite event, JsonNode desired) {
        EventRevision latest = latest(event.id);
        if ("DRAFT".equals(latest.submissionStatus)) {
            latest.configurationJson = desired;
            return revisions.save(latest);
        }
        return cloneRevision(event, latest, desired);
    }

    private EventRevision cloneRevision(EventSite event, EventRevision source, JsonNode desired) {
        EventRevision created = new EventRevision();
        created.eventId = event.id;
        created.revisionNumber = source.revisionNumber + 1;
        created.configurationJson = desired;
        created.submissionStatus = "DRAFT";
        revisions.save(created);
        editorDocuments.findByRevisionId(source.id).ifPresent(existing -> {
            EditorDocument copy = new EditorDocument();
            copy.eventId = event.id;
            copy.revisionId = created.id;
            copy.schemaVersion = existing.schemaVersion;
            copy.documentJson = existing.documentJson.deepCopy();
            copy.versionNumber = existing.versionNumber;
            editorDocuments.save(copy);
        });
        if (!"PUBLISHED".equals(event.status) && !"SUSPENDED".equals(event.status) && !"EXPIRED".equals(event.status)) {
            event.status = "DRAFT";
            event.approvedRevisionId = null;
        }
        return created;
    }

    private void ensureNoQueue(UUID eventId) {
        boolean queued = revisions.findByEventIdOrderByRevisionNumberAsc(eventId).stream()
                .anyMatch(revision -> "SUBMITTED".equals(revision.submissionStatus) || "IN_REVIEW".equals(revision.submissionStatus));
        if (queued) {
            throw ApiException.conflict("This site is already with a reviewer.");
        }
    }

    private void assertMedia(UUID eventId, JsonNode configuration) {
        for (String id : sanitizer.mediaIds(configuration)) {
            try {
                media.findByIdAndEventId(UUID.fromString(id), eventId)
                        .orElseThrow(() -> ApiException.badRequest("An uploaded file is no longer on this site."));
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest("An uploaded file is no longer on this site.");
            }
        }
    }

    private ObjectNode defaults(TemplateVersion version, String eventName) {
        try {
            ObjectNode copy = (ObjectNode) mapper.readTree(version.defaultConfiguration.toString());
            if (copy.get("event") instanceof ObjectNode event) {
                event.put("name", eventName);
            }
            return sanitizer.sanitize(copy, false);
        } catch (Exception exception) {
            throw ApiException.badRequest("The template configuration could not be copied.");
        }
    }

    private Map<String, Object> reviewView(EventReview review) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", review.id);
        body.put("decision", review.decision);
        body.put("comments", review.comments);
        body.put("createdAt", review.createdAt);
        body.put("reviewerId", review.reviewerId);
        return body;
    }
}

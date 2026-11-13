package com.prism.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.media.MediaRepository;
import com.prism.revision.EventRevision;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EditorDocumentService {

    private final EditorDocumentRepository documents;
    private final EditorVersionRepository versions;
    private final EventService events;
    private final MediaRepository media;
    private final DocumentValidator validator;
    private final ObjectMapper mapper;
    private final PrismProperties properties;

    public EditorDocumentService(
            EditorDocumentRepository documents,
            EditorVersionRepository versions,
            EventService events,
            MediaRepository media,
            ObjectMapper mapper,
            PrismProperties properties
    ) {
        this.documents = documents;
        this.versions = versions;
        this.events = events;
        this.media = media;
        this.validator = new DocumentValidator(mapper);
        this.mapper = mapper;
        this.properties = properties;
    }

    public void requireEnabled() {
        if (!properties.isAdvancedEditorEnabled()) {
            throw ApiException.forbidden("The advanced editor is turned off.");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> read(UUID eventId, UserPrincipal principal) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        EventRevision revision = events.latest(event.id);
        EditorDocument document = documents.findByRevisionId(revision.id)
                .orElseThrow(() -> ApiException.notFound("This event has no visual page yet."));
        return view(document, revision);
    }

    @Transactional
    public Map<String, Object> convert(UUID eventId, UserPrincipal principal) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        EventRevision revision = draft(event);
        if (documents.findByRevisionId(revision.id).isPresent()) {
            throw ApiException.conflict("This draft already has a visual page.");
        }
        JsonNode configuration = revision.configurationJson;
        ObjectNode document = blank(event.eventName, text(configuration, "subtitle"));
        ArrayNode children = (ArrayNode) document.path("root").path("children");
        if (configuration.path("canvas").path("elements").isArray()) {
            for (JsonNode element : configuration.path("canvas").path("elements")) {
                ObjectNode converted = fromCanvas(element);
                if (converted != null) {
                    children.add(converted);
                }
            }
        }
        if (children.isEmpty()) {
            children.add(heading(text(configuration, "title", event.eventName)));
            children.add(paragraph(text(configuration, "subtitle")));
        }
        return store(event, revision, validator.sanitize(document), principal.id(), true);
    }

    @Transactional
    public Map<String, Object> save(UUID eventId, JsonNode document, int version, UserPrincipal principal, boolean history) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        EventRevision revision = draft(event);
        ObjectNode clean = validator.sanitize(document);
        assertMedia(event.id, clean);
        EditorDocument current = documents.findByRevisionId(revision.id)
                .orElseThrow(() -> ApiException.notFound("This event has no visual page yet."));
        if (current.versionNumber != version) {
            throw ApiException.conflict("This page was saved somewhere else. Reload it before saving again.");
        }
        current.documentJson = clean;
        current.schemaVersion = DocumentValidator.SCHEMA;
        current.versionNumber = version + 1;
        current.updatedAt = Instant.now();
        documents.save(current);
        if (history) {
            snapshot(current, principal.id());
        }
        return view(current, revision);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID eventId, UserPrincipal principal) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        EditorDocument document = documents.findByRevisionId(events.latest(event.id).id)
                .orElseThrow(() -> ApiException.notFound("This event has no visual page yet."));
        return versions.findByDocumentIdOrderByVersionNumberDesc(document.id).stream().map(version -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", version.id);
            row.put("version", version.versionNumber);
            row.put("createdAt", version.createdAt);
            return row;
        }).toList();
    }

    @Transactional
    public Map<String, Object> restore(UUID eventId, UUID versionId, UserPrincipal principal) {
        requireEnabled();
        EventSite event = events.requireOwner(eventId, principal);
        EventRevision revision = draft(event);
        EditorDocument current = documents.findByRevisionId(revision.id)
                .orElseThrow(() -> ApiException.notFound("This event has no visual page yet."));
        EditorDocumentVersion version = versions.findById(versionId)
                .filter(row -> row.documentId.equals(current.id))
                .orElseThrow(() -> ApiException.notFound("That version was not found."));
        ObjectNode clean = validator.sanitize(version.documentJson);
        current.documentJson = clean;
        current.versionNumber = current.versionNumber + 1;
        current.updatedAt = Instant.now();
        documents.save(current);
        snapshot(current, principal.id());
        return view(current, revision);
    }

    @Transactional
    public void install(EventSite event, EventRevision revision, JsonNode document, UUID userId) {
        ObjectNode clean = validator.sanitize(document);
        assertMedia(event.id, clean);
        documents.findByRevisionId(revision.id).ifPresentOrElse(current -> {
            current.documentJson = clean;
            current.schemaVersion = DocumentValidator.SCHEMA;
            current.versionNumber = current.versionNumber + 1;
            current.updatedAt = Instant.now();
            documents.save(current);
            snapshot(current, userId);
        }, () -> store(event, revision, clean, userId, true));
    }

    public ObjectNode validate(UUID eventId, JsonNode document, UserPrincipal principal) {
        requireEnabled();
        events.requireOwner(eventId, principal);
        return validator.sanitize(document);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> review(UUID eventId, UserPrincipal principal) {
        EventSite event = events.requireReadable(eventId, principal);
        EventRevision revision = events.latest(event.id);
        EditorDocument document = documents.findByRevisionId(revision.id)
                .orElseThrow(() -> ApiException.notFound("This event has no visual page yet."));
        return view(document, revision);
    }

    public JsonNode published(UUID revisionId) {
        return documents.findByRevisionId(revisionId).map(document -> document.documentJson).orElse(null);
    }

    private Map<String, Object> store(EventSite event, EventRevision revision, ObjectNode clean, UUID userId, boolean history) {
        assertMedia(event.id, clean);
        EditorDocument created = new EditorDocument();
        created.eventId = event.id;
        created.revisionId = revision.id;
        created.schemaVersion = DocumentValidator.SCHEMA;
        created.documentJson = clean;
        created.versionNumber = 1;
        documents.save(created);
        if (history) {
            snapshot(created, userId);
        }
        return view(created, revision);
    }

    private void snapshot(EditorDocument document, UUID userId) {
        EditorDocumentVersion version = new EditorDocumentVersion();
        version.documentId = document.id;
        version.versionNumber = document.versionNumber;
        version.documentJson = document.documentJson.deepCopy();
        version.createdBy = userId;
        versions.save(version);
    }

    private EventRevision draft(EventSite event) {
        EventRevision revision = events.latest(event.id);
        if (!"DRAFT".equals(revision.submissionStatus)) {
            throw ApiException.badRequest("Save a draft before editing the visual page.");
        }
        return revision;
    }

    private void assertMedia(UUID eventId, JsonNode document) {
        for (String id : validator.mediaIds(document)) {
            try {
                media.findByIdAndEventId(UUID.fromString(id), eventId)
                        .orElseThrow(() -> ApiException.badRequest("Choose a picture from this event."));
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest("Choose a picture from this event.");
            }
        }
    }

    private Map<String, Object> view(EditorDocument document, EventRevision revision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revisionId", revision.id);
        body.put("submissionStatus", revision.submissionStatus);
        body.put("version", document.versionNumber);
        body.put("updatedAt", document.updatedAt);
        body.put("document", document.documentJson);
        return body;
    }

    private ObjectNode blank(String title, String description) {
        ObjectNode document = mapper.createObjectNode();
        document.put("schemaVersion", 1);
        document.put("documentId", UUID.randomUUID().toString());
        document.putObject("page").put("title", title == null ? "" : title).put("description", description == null ? "" : description);
        document.putObject("breakpoints").put("desktop", ResponsiveBreakpoints.DESKTOP_MIN).put("tablet", ResponsiveBreakpoints.TABLET_MIN).put("mobile", ResponsiveBreakpoints.MOBILE_MAX);
        ObjectNode tokens = document.putObject("designTokens");
        tokens.putObject("colors");
        tokens.putObject("fonts");
        tokens.putObject("spacing");
        ObjectNode root = document.putObject("root");
        root.put("id", "page");
        root.put("type", "SECTION");
        root.put("name", "Page");
        root.putObject("props");
        ObjectNode styles = root.putObject("styles");
        styles.put("display", "flex");
        styles.put("flexDirection", "column");
        styles.put("gap", "24px");
        styles.put("padding", "32px");
        ObjectNode responsive = root.putObject("responsiveStyles");
        responsive.putObject("tablet");
        responsive.putObject("mobile");
        root.putArray("children");
        return document;
    }

    private ObjectNode heading(String text) {
        ObjectNode node = leaf("HEADING", "Heading");
        node.putObject("props").put("text", text).put("level", 1);
        node.putObject("styles").put("fontSize", "48px").put("fontFamily", "Fraunces");
        return node;
    }

    private ObjectNode paragraph(String text) {
        ObjectNode node = leaf("PARAGRAPH", "Text");
        node.putObject("props").put("text", text);
        node.putObject("styles").put("fontSize", "18px");
        return node;
    }

    private ObjectNode fromCanvas(JsonNode element) {
        String type = element.path("type").asText("");
        if ("text".equals(type)) {
            ObjectNode node = "display".equals(element.path("weight").asText("")) ? heading(element.path("text").asText("")) : paragraph(element.path("text").asText(""));
            if (!element.path("color").asText("").isBlank()) {
                ((ObjectNode) node.get("styles")).put("color", element.path("color").asText(""));
            }
            return node;
        }
        if ("button".equals(type)) {
            ObjectNode node = leaf("BUTTON", "Button");
            node.putObject("props").put("text", element.path("text").asText("Button")).put("href", element.path("href").asText(""));
            node.putObject("styles").put("padding", "12px 20px");
            return node;
        }
        if ("image".equals(type) && !element.path("mediaId").asText("").isBlank()) {
            ObjectNode node = leaf("IMAGE", "Picture");
            node.putObject("props").put("mediaId", element.path("mediaId").asText("")).put("alt", "");
            node.putObject("styles").put("width", "100%").put("objectFit", "cover");
            return node;
        }
        return null;
    }

    private ObjectNode leaf(String type, String name) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", UUID.randomUUID().toString().substring(0, 8));
        node.put("type", type);
        node.put("name", name);
        node.putObject("responsiveStyles").putObject("tablet");
        ((ObjectNode) node.get("responsiveStyles")).putObject("mobile");
        node.putArray("children");
        return node;
    }

    private String text(JsonNode configuration, String field) {
        return text(configuration, field, "");
    }

    private String text(JsonNode configuration, String field, String fallback) {
        String direct = configuration.path("event").path(field).asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        for (JsonNode section : configuration.path("sections")) {
            String value = section.path("properties").path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }
}

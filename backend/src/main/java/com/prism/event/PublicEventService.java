package com.prism.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.prism.analytics.EventViewRepository;
import com.prism.editor.EditorDocumentRepository;
import com.prism.hosting.HostingService;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.exception.ApiException;
import com.prism.template.EventTemplate;
import com.prism.template.TemplateRepository;
import com.prism.template.TemplateVersion;
import com.prism.template.TemplateVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PublicEventService {

    private final EventRepository events;
    private final RevisionRepository revisions;
    private final TemplateVersionRepository versions;
    private final TemplateRepository templates;
    private final HostingService hosting;
    private final ConfigurationSanitizer sanitizer;
    private final JdbcTemplate jdbc;
    private final EventViewRepository views;
    private final EditorDocumentRepository editorDocuments;

    public PublicEventService(
            EventRepository events,
            RevisionRepository revisions,
            TemplateVersionRepository versions,
            TemplateRepository templates,
            HostingService hosting,
            ConfigurationSanitizer sanitizer,
            JdbcTemplate jdbc,
            EventViewRepository views,
            EditorDocumentRepository editorDocuments
    ) {
        this.events = events;
        this.revisions = revisions;
        this.versions = versions;
        this.templates = templates;
        this.hosting = hosting;
        this.sanitizer = sanitizer;
        this.jdbc = jdbc;
        this.views = views;
        this.editorDocuments = editorDocuments;
    }

    @Transactional
    public Map<String, Object> resolve(String slug, boolean countView) {
        EventSite event = events.findBySlug(slug).orElseThrow(() -> ApiException.notFound("This page is not available."));
        if ("SUSPENDED".equals(event.status)) {
            return shell(event, "SUSPENDED", null, null, null);
        }
        boolean live = "PUBLISHED".equals(event.status) && hosting.covers(event.id, Instant.now()) && event.publishedRevisionId != null;
        if ("EXPIRED".equals(event.status) || ("PUBLISHED".equals(event.status) && !live)) {
            return shell(event, "EXPIRED", null, null, null);
        }
        if (!live) {
            throw ApiException.notFound("This page is not available.");
        }
        EventRevision revision = revisions.findById(event.publishedRevisionId)
                .orElseThrow(() -> ApiException.notFound("This page is not available."));
        if (countView) {
            jdbc.update("""
                    insert into event_views (event_id, viewed_on, view_count)
                    values (?, ?, 1)
                    on conflict (event_id, viewed_on)
                    do update set view_count = event_views.view_count + 1
                    """, event.id, LocalDate.now(ZoneOffset.UTC));
        }
        TemplateVersion version = versions.findById(event.templateVersionId).orElse(null);
        EventTemplate template = version == null ? null : templates.findById(version.templateId).orElse(null);
        return shell(event, "PUBLISHED", publicConfiguration(revision.configurationJson), template == null ? null : template.code,
                publicDocument(editorDocuments.findByRevisionId(revision.id).map(document -> document.documentJson).orElse(null)));
    }

    public long views(java.util.UUID eventId) {
        return views.totalForEvent(eventId);
    }

    private Map<String, Object> shell(EventSite event, String availability, JsonNode configuration, String templateCode, JsonNode editorDocument) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("slug", event.slug);
        body.put("eventName", event.eventName);
        body.put("availability", availability);
        body.put("templateCode", templateCode);
        body.put("configuration", configuration);
        if (editorDocument != null) {
            body.put("editorDocument", editorDocument);
        }
        if (configuration != null) {
            String description = configuration.path("event").path("description").asText("");
            if (description.isBlank()) {
                description = configuration.path("event").path("subtitle").asText(event.eventName);
            }
            body.put("seo", Map.of(
                    "title", event.eventName,
                    "description", description.length() > 180 ? description.substring(0, 180) : description
            ));
        }
        return body;
    }

    private JsonNode publicDocument(JsonNode source) {
        if (source == null) {
            return null;
        }
        JsonNode copy = source.deepCopy();
        rewriteEditorMedia(copy.path("root"));
        return copy;
    }

    private void rewriteEditorMedia(JsonNode node) {
        if (node instanceof ObjectNode object && "IMAGE".equals(object.path("type").asText())) {
            JsonNode props = object.get("props");
            if (props instanceof ObjectNode properties) {
                String mediaId = properties.path("mediaId").asText("");
                if (!mediaId.isBlank() && !mediaId.startsWith("/")) {
                    properties.put("mediaId", "/api/v1/public/media/" + mediaId);
                }
            }
        }
        for (JsonNode child : node.path("children")) {
            rewriteEditorMedia(child);
        }
    }

    private JsonNode publicConfiguration(JsonNode source) {
        ObjectNode copy = sanitizer.sanitize(source, false);
        rewrite(copy);
        return copy;
    }

    private void rewrite(ObjectNode configuration) {
        for (JsonNode section : configuration.path("sections")) {
            if (!(section instanceof ObjectNode object) || !(object.get("properties") instanceof ObjectNode properties)) {
                continue;
            }
            rewriteField(properties, "backgroundImage");
            rewriteField(properties, "mediaId");
            if (properties.path("mediaIds").isArray()) {
                var ids = properties.withArray("mediaIds");
                for (int i = 0; i < ids.size(); i++) {
                    String value = ids.get(i).asText("");
                    if (!value.isBlank() && !value.startsWith("/")) {
                        ids.set(i, TextNode.valueOf("/api/v1/public/media/" + value));
                    }
                }
            }
        }
    }

    private void rewriteField(ObjectNode properties, String field) {
        String value = properties.path(field).asText("");
        if (!value.isBlank() && !value.startsWith("/")) {
            properties.put(field, "/api/v1/public/media/" + value);
        }
    }
}

package com.prism.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.editor.DocumentValidator;
import com.prism.editor.EditorDocument;
import com.prism.editor.EditorDocumentRepository;
import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.revision.EventRevision;
import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImageDocumentBinder {

    private final EventRepository eventRepository;
    private final EventService events;
    private final EditorDocumentRepository documents;
    private final DocumentValidator validator;
    private final ObjectMapper mapper;

    public ImageDocumentBinder(
            EventRepository eventRepository,
            EventService events,
            EditorDocumentRepository documents,
            ObjectMapper mapper
    ) {
        this.eventRepository = eventRepository;
        this.events = events;
        this.documents = documents;
        this.mapper = mapper;
        this.validator = new DocumentValidator(mapper);
    }

    @Transactional
    public void bind(UUID eventId, ImagePlanItem plan, UUID mediaId, String alt) {
        EventSite event = eventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("That event site was not found."));
        EventRevision revision = events.latest(event.id);
        if (!"DRAFT".equals(revision.submissionStatus)) {
            throw ApiException.badRequest("Save a draft before updating generated images.");
        }
        EditorDocument document = documents.findByRevisionId(revision.id)
                .orElseThrow(() -> ApiException.notFound("This event has no visual page yet."));
        ObjectNode root = document.documentJson.deepCopy();
        boolean changed = apply(root.path("root"), plan, mediaId.toString(), alt == null ? "" : alt);
        if (!changed) {
            ensureImageNode(root, plan, mediaId.toString(), alt == null ? "" : alt);
        }
        ObjectNode clean = validator.sanitize(root);
        document.documentJson = clean;
        document.versionNumber = document.versionNumber + 1;
        document.updatedAt = Instant.now();
        documents.save(document);
    }

    @Transactional
    public void unbindMedia(UUID eventId, UUID mediaId) {
        EventRevision revision = events.latest(eventId);
        documents.findByRevisionId(revision.id).ifPresent(document -> {
            ObjectNode root = document.documentJson.deepCopy();
            if (clearMedia(root.path("root"), mediaId.toString())) {
                document.documentJson = validator.sanitize(root);
                document.versionNumber = document.versionNumber + 1;
                document.updatedAt = Instant.now();
                documents.save(document);
            }
        });
    }

    private boolean apply(JsonNode node, ImagePlanItem plan, String mediaId, String alt) {
        if (node == null || !node.isObject()) {
            return false;
        }
        String type = node.path("type").asText("");
        String id = node.path("id").asText("");
        ObjectNode props = node.has("props") && node.get("props").isObject()
                ? (ObjectNode) node.get("props")
                : ((ObjectNode) node).putObject("props");
        boolean match = false;
        if ("IMAGE".equals(type)) {
            String assetKey = props.path("assetKey").asText("");
            if (!plan.assetKey().isBlank() && plan.assetKey().equalsIgnoreCase(assetKey)) {
                match = true;
            } else if (!plan.targetNodeId().isBlank() && plan.targetNodeId().equals(id)) {
                match = true;
            } else if (!plan.sectionId().isBlank() && plan.sectionId().equalsIgnoreCase(id)) {
                match = true;
            } else if (props.path("mediaId").asText("").isBlank() && "hero-main".equals(plan.assetKey())) {
                match = nearestHeroCandidate(node);
            }
            if (match) {
                props.put("mediaId", mediaId.toLowerCase(Locale.ROOT));
                props.put("assetKey", plan.assetKey());
                if (!alt.isBlank() || props.path("alt").asText("").isBlank()) {
                    props.put("alt", alt.isBlank() ? defaultAlt(plan) : alt);
                }
                return true;
            }
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                if (apply(child, plan, mediaId, alt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureImageNode(ObjectNode document, ImagePlanItem plan, String mediaId, String alt) {
        JsonNode root = document.path("root");
        if (!root.isObject()) {
            return;
        }
        ObjectNode section = findSection((ObjectNode) root, plan.sectionId());
        if (section == null) {
            section = (ObjectNode) root;
        }
        ObjectNode image = mapper.createObjectNode();
        image.put("id", "img-" + plan.assetKey());
        image.put("type", "IMAGE");
        image.put("name", plan.purpose() == null || plan.purpose().isBlank() ? plan.assetKey() : plan.purpose());
        ObjectNode props = image.putObject("props");
        props.put("mediaId", mediaId.toLowerCase(Locale.ROOT));
        props.put("alt", alt.isBlank() ? defaultAlt(plan) : alt);
        props.put("assetKey", plan.assetKey());
        image.putObject("styles");
        image.putArray("children");
        if (!section.has("children") || !section.get("children").isArray()) {
            section.putArray("children");
        }
        ((com.fasterxml.jackson.databind.node.ArrayNode) section.get("children")).insert(0, image);
    }

    private ObjectNode findSection(ObjectNode root, String sectionId) {
        if (sectionId == null || sectionId.isBlank()) {
            return firstSection(root);
        }
        return findById(root, sectionId);
    }

    private ObjectNode firstSection(JsonNode node) {
        if (node != null && node.isObject() && "SECTION".equals(node.path("type").asText())) {
            return (ObjectNode) node;
        }
        JsonNode children = node == null ? null : node.path("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                ObjectNode found = firstSection(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ObjectNode findById(JsonNode node, String id) {
        if (node != null && node.isObject() && id.equals(node.path("id").asText())) {
            return (ObjectNode) node;
        }
        JsonNode children = node == null ? null : node.path("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                ObjectNode found = findById(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean clearMedia(JsonNode node, String mediaId) {
        if (node == null || !node.isObject()) {
            return false;
        }
        boolean changed = false;
        if ("IMAGE".equals(node.path("type").asText("")) && node.path("props").isObject()) {
            ObjectNode props = (ObjectNode) node.get("props");
            if (mediaId.equalsIgnoreCase(props.path("mediaId").asText(""))) {
                props.put("mediaId", "");
                changed = true;
            }
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                changed = clearMedia(child, mediaId) || changed;
            }
        }
        return changed;
    }

    private static boolean nearestHeroCandidate(JsonNode node) {
        String name = node.path("name").asText("").toLowerCase(Locale.ROOT);
        return name.contains("hero") || name.contains("cover") || name.contains("banner");
    }

    private static String defaultAlt(ImagePlanItem plan) {
        if (plan.purpose() != null && !plan.purpose().isBlank()) {
            return plan.purpose();
        }
        return plan.assetKey().replace('-', ' ');
    }
}

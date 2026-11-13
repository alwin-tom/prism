package com.prism.rsvp;

import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.event.PublicEventService;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.AuthContext;
import com.prism.shared.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RsvpController {

    private final RsvpService rsvps;
    private final EventService events;
    private final EventRepository eventRepository;
    private final RevisionRepository revisions;
    private final PublicEventService publicEvents;

    public RsvpController(
            RsvpService rsvps,
            EventService events,
            EventRepository eventRepository,
            RevisionRepository revisions,
            PublicEventService publicEvents
    ) {
        this.rsvps = rsvps;
        this.events = events;
        this.eventRepository = eventRepository;
        this.revisions = revisions;
        this.publicEvents = publicEvents;
    }

    @PostMapping("/public/events/{slug}/rsvp")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String slug, @RequestBody RsvpService.RsvpCommand command, HttpServletRequest http) {
        EventSite event = live(slug);
        EventRevision revision = revisions.findById(event.publishedRevisionId).orElseThrow(() -> ApiException.notFound("This page is not available."));
        return ApiResponse.ok(rsvps.submit(event, revision, command, Requests.clientIp(http)));
    }

    @GetMapping("/events/{id}/rsvps")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable UUID id) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        return ApiResponse.ok(rsvps.list(event.id));
    }

    @GetMapping("/events/{id}/rsvps/summary")
    public ApiResponse<Map<String, Object>> summary(@PathVariable UUID id) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        Map<String, Object> summary = new java.util.LinkedHashMap<>(rsvps.summary(event.id));
        summary.put("pageViews", publicEvents.views(event.id));
        return ApiResponse.ok(summary);
    }

    @GetMapping("/events/{id}/rsvps/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + event.slug + "-rsvps.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(rsvps.csv(event.id));
    }

    @DeleteMapping("/events/{id}/rsvps/{rsvpId}")
    public ApiResponse<Void> delete(@PathVariable UUID id, @PathVariable UUID rsvpId) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        rsvps.delete(event, rsvpId);
        return ApiResponse.ok(null);
    }

    private EventSite live(String slug) {
        Map<String, Object> resolved = publicEvents.resolve(slug, false);
        if (!"PUBLISHED".equals(resolved.get("availability"))) {
            throw ApiException.badRequest("This site is not taking replies.");
        }
        return eventRepository.findBySlug(slug).orElseThrow(() -> ApiException.notFound("This page is not available."));
    }
}

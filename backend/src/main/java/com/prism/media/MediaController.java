package com.prism.media;

import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.AuthContext;
import com.prism.shared.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MediaController {

    private final MediaService media;
    private final MediaRepository mediaRepository;
    private final EventService events;
    private final EventRepository eventRepository;

    public MediaController(MediaService media, MediaRepository mediaRepository, EventService events, EventRepository eventRepository) {
        this.media = media;
        this.mediaRepository = mediaRepository;
        this.events = events;
        this.eventRepository = eventRepository;
    }

    @PostMapping("/events/{id}/media")
    public ApiResponse<Map<String, Object>> upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file, HttpServletRequest http) throws IOException {
        var principal = AuthContext.require();
        EventSite event = events.requireOwner(id, principal);
        return ApiResponse.ok(media.store(event, file, principal, Requests.clientIp(http)));
    }

    @GetMapping("/events/{id}/media")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable UUID id) {
        EventSite event = events.requireReadable(id, AuthContext.require());
        return ApiResponse.ok(mediaRepository.findByEventIdOrderByCreatedAtAsc(event.id).stream()
                .map(row -> media.view(row, false)).toList());
    }

    @GetMapping("/events/{eventId}/media/{mediaId}")
    public ResponseEntity<Resource> download(@PathVariable UUID eventId, @PathVariable UUID mediaId) {
        EventSite event = events.requireReadable(eventId, AuthContext.require());
        return file(media.requireOwned(event, mediaId), true);
    }

    @DeleteMapping("/events/{eventId}/media/{mediaId}")
    public ApiResponse<Void> delete(@PathVariable UUID eventId, @PathVariable UUID mediaId) throws IOException {
        EventSite event = events.requireOwner(eventId, AuthContext.require());
        media.delete(event, mediaId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/public/media/{mediaId}")
    public ResponseEntity<Resource> publicMedia(@PathVariable UUID mediaId) {
        EventMedia row = mediaRepository.findById(mediaId).orElseThrow(() -> ApiException.notFound("That file was not found."));
        EventSite event = eventRepository.findById(row.eventId).orElseThrow(() -> ApiException.notFound("That file was not found."));
        return file(media.requirePublic(event, mediaId), false);
    }

    private ResponseEntity<Resource> file(EventMedia row, boolean privateCache) {
        Resource resource = media.resource(row, false);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(row.mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + row.storedFilename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, privateCache ? "private, no-store" : "public, max-age=86400")
                .body(resource);
    }
}

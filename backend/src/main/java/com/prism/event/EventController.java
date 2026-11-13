package com.prism.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.pricing.PricingService;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.api.PageMeta;
import com.prism.shared.security.AuthContext;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Pages;
import com.prism.shared.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EventController {

    private final EventService events;
    private final EventDeletionService deletion;
    private final EventRepository repository;
    private final PricingService pricing;

    public EventController(EventService events, EventDeletionService deletion, EventRepository repository, PricingService pricing) {
        this.events = events;
        this.deletion = deletion;
        this.repository = repository;
        this.pricing = pricing;
    }

    @PostMapping("/events")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateEvent request, HttpServletRequest http) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(events.create(principal, request.categoryId(), request.templateId(), request.eventName(), request.slug(), request.requestedDays(), Requests.clientIp(http)));
    }

    @GetMapping("/events")
    public ApiResponse<java.util.List<Map<String, Object>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UserPrincipal principal = AuthContext.require();
        Page<EventSite> result = repository.findByUserId(principal.id(), Pages.of(page, size, "updatedAt,desc", "updatedAt", Set.of("updatedAt", "eventName", "createdAt")));
        return ApiResponse.page(result.map(event -> events.present(event, events.latest(event.id))).toList(), PageMeta.of(result));
    }

    @GetMapping("/events/slug-available")
    public ApiResponse<Map<String, Object>> slug(@RequestParam String slug, @RequestParam(required = false) UUID eventId) {
        AuthContext.require();
        return ApiResponse.ok(events.availability(slug, eventId));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<Map<String, Object>> one(@PathVariable UUID id) {
        EventSite event = events.requireReadable(id, AuthContext.require());
        return ApiResponse.ok(events.present(event, events.latest(event.id)));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        UserPrincipal principal = AuthContext.require();
        EventSite event = events.requireDeletable(id, principal);
        deletion.delete(event, principal, Requests.clientIp(http));
        return ApiResponse.ok(null);
    }

    @PutMapping("/events/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable UUID id, @RequestBody UpdateEvent request, HttpServletRequest http) {
        UserPrincipal principal = AuthContext.require();
        EventSite event = events.requireOwner(id, principal);
        return ApiResponse.ok(events.update(event, request.eventName(), request.slug(), request.requestedDays(), principal, Requests.clientIp(http)));
    }

    @PutMapping("/events/{id}/configuration")
    public ApiResponse<Map<String, Object>> configuration(@PathVariable UUID id, @RequestBody JsonNode configuration) {
        UserPrincipal principal = AuthContext.require();
        EventSite event = events.requireOwner(id, principal);
        return ApiResponse.ok(events.saveConfiguration(event, configuration, principal));
    }

    @GetMapping("/events/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable UUID id) {
        EventSite event = events.requireReadable(id, AuthContext.require());
        return ApiResponse.ok(events.present(event, events.latest(event.id)));
    }

    @PostMapping("/events/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable UUID id, HttpServletRequest http) {
        UserPrincipal principal = AuthContext.require();
        EventSite event = events.requireOwner(id, principal);
        return ApiResponse.ok(events.submit(event, principal, Requests.clientIp(http)));
    }

    @GetMapping("/events/{id}/quote")
    public ApiResponse<PricingService.Quote> quote(@PathVariable UUID id, @RequestParam(required = false) Integer days) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        return ApiResponse.ok(pricing.quote(days == null ? event.requestedDays : days));
    }

    public record CreateEvent(
            @NotNull UUID categoryId,
            @NotNull UUID templateId,
            @NotBlank String eventName,
            @NotBlank String slug,
            @Min(1) @Max(365) int requestedDays
    ) {
    }

    public record UpdateEvent(String eventName, String slug, Integer requestedDays) {
    }
}

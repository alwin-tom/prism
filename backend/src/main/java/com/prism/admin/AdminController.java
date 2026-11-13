package com.prism.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.category.CategoryRepository;
import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.payment.PaymentRepository;
import com.prism.payment.PaymentService;
import com.prism.review.ReviewService;
import com.prism.revision.RevisionRepository;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.api.PageMeta;
import com.prism.shared.security.AuthContext;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Pages;
import com.prism.shared.util.Requests;
import com.prism.template.TemplateRepository;
import com.prism.template.TemplateVersionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService admin;
    private final ReviewService reviews;
    private final EventService events;
    private final EventRepository eventRepository;
    private final RevisionRepository revisions;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final CategoryRepository categories;
    private final TemplateRepository templates;
    private final TemplateVersionRepository versions;

    public AdminController(
            AdminService admin,
            ReviewService reviews,
            EventService events,
            EventRepository eventRepository,
            RevisionRepository revisions,
            PaymentRepository payments,
            PaymentService paymentService,
            CategoryRepository categories,
            TemplateRepository templates,
            TemplateVersionRepository versions
    ) {
        this.admin = admin;
        this.reviews = reviews;
        this.events = events;
        this.eventRepository = eventRepository;
        this.revisions = revisions;
        this.payments = payments;
        this.paymentService = paymentService;
        this.categories = categories;
        this.templates = templates;
        this.versions = versions;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(admin.dashboard());
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> users(@RequestParam(required = false) String query, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var result = admin.users(query, Pages.of(page, size, "createdAt,desc", "createdAt", Set.of("createdAt", "fullName")));
        return ApiResponse.page(result.getContent(), PageMeta.of(result));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> createUser(@Valid @RequestBody StaffRequest request, HttpServletRequest http) {
        UserPrincipal actor = AuthContext.require();
        return ApiResponse.ok(admin.createStaff(request.fullName(), request.phoneNumber(), request.password(), request.role(), actor, Requests.clientIp(http)));
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> status(@PathVariable UUID id, @RequestBody StatusRequest request, HttpServletRequest http) {
        return ApiResponse.ok(admin.setStatus(id, request.status(), AuthContext.require(), Requests.clientIp(http)));
    }

    @GetMapping("/reviews")
    public ApiResponse<?> queue(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Pageable pageable = Pages.of(page, size, "submittedAt,asc", "submittedAt", Set.of("submittedAt"));
        var result = revisions.findBySubmissionStatusInOrderBySubmittedAtAsc(java.util.List.of("SUBMITTED", "IN_REVIEW"), pageable);
        var rows = result.getContent().stream().map(revision -> {
            var event = eventRepository.findById(revision.eventId).orElse(null);
            return event == null ? null : events.present(event, revision);
        }).filter(java.util.Objects::nonNull).toList();
        return ApiResponse.page(rows, PageMeta.of(result));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<?> event(@PathVariable UUID id) {
        return ApiResponse.ok(reviews.open(id, AuthContext.require()));
    }

    @PostMapping("/events/{id}/approve")
    public ApiResponse<?> approve(@PathVariable UUID id, @RequestBody(required = false) CommentRequest request, HttpServletRequest http) {
        return ApiResponse.ok(reviews.decide(id, AuthContext.require(), "APPROVED", request == null ? "" : request.comments(), Requests.clientIp(http)));
    }

    @PostMapping("/events/{id}/request-changes")
    public ApiResponse<?> changes(@PathVariable UUID id, @RequestBody CommentRequest request, HttpServletRequest http) {
        return ApiResponse.ok(reviews.decide(id, AuthContext.require(), "CHANGES_REQUESTED", request.comments(), Requests.clientIp(http)));
    }

    @PostMapping("/events/{id}/reject")
    public ApiResponse<?> reject(@PathVariable UUID id, @RequestBody CommentRequest request, HttpServletRequest http) {
        return ApiResponse.ok(reviews.decide(id, AuthContext.require(), "REJECTED", request.comments(), Requests.clientIp(http)));
    }

    @PostMapping("/events/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> suspend(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(admin.suspend(id, AuthContext.require(), Requests.clientIp(http)));
    }

    @PostMapping("/events/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> unpublish(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(admin.unpublish(id, AuthContext.require(), Requests.clientIp(http)));
    }

    @PostMapping("/events/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> restore(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(admin.restore(id, AuthContext.require(), Requests.clientIp(http)));
    }

    @GetMapping("/events")
    public ApiResponse<?> events(@RequestParam(required = false) String status, @RequestParam(required = false) String query, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var result = eventRepository.search(blank(status), blank(query), Pages.of(page, size, "updatedAt,desc", "updatedAt", Set.of("updatedAt", "eventName", "status")));
        return ApiResponse.page(result.map(event -> events.present(event, events.latest(event.id))).toList(), PageMeta.of(result));
    }

    @GetMapping("/payments")
    public ApiResponse<?> payments(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var result = payments.findAllByOrderByCreatedAtDesc(Pages.of(page, size, "createdAt,desc", "createdAt", Set.of("createdAt")));
        return ApiResponse.page(result.map(paymentService::view).toList(), PageMeta.of(result));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> audit(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var result = admin.audit(Pages.of(page, size, "createdAt,desc", "createdAt", Set.of("createdAt")));
        return ApiResponse.page(result.getContent(), PageMeta.of(result));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> categories() {
        return ApiResponse.ok(categories.findAll());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> createCategory(@RequestBody CategoryRequest request) {
        return ApiResponse.ok(admin.saveCategory(null, request.name(), request.code(), request.description(), request.active() == null || request.active()));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> updateCategory(@PathVariable UUID id, @RequestBody CategoryRequest request) {
        return ApiResponse.ok(admin.saveCategory(id, request.name(), request.code(), request.description(), request.active() == null || request.active()));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> templates() {
        return ApiResponse.ok(templates.findAll());
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> updateTemplate(@PathVariable UUID id, @RequestBody TemplateRequest request) {
        return ApiResponse.ok(admin.saveTemplate(id, request.categoryId(), request.name(), request.description(), request.active() == null || request.active()));
    }

    @GetMapping("/templates/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> versions(@PathVariable UUID id) {
        return ApiResponse.ok(versions.findByTemplateIdOrderByVersionNumberDesc(id));
    }

    @PostMapping("/templates/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> addVersion(@PathVariable UUID id, @RequestBody VersionRequest request) {
        return ApiResponse.ok(admin.addVersion(id, request.schemaJson(), request.defaultConfiguration(), request.active() == null || request.active()));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record StaffRequest(@NotBlank String fullName, @NotBlank String phoneNumber, @NotBlank String password, @NotBlank String role) {
    }

    public record StatusRequest(String status) {
    }

    public record CommentRequest(String comments) {
    }

    public record CategoryRequest(String name, String code, String description, Boolean active) {
    }

    public record TemplateRequest(UUID categoryId, String name, String description, Boolean active) {
    }

    public record VersionRequest(JsonNode schemaJson, JsonNode defaultConfiguration, Boolean active) {
    }
}

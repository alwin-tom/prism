package com.prism.moderation;

import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.api.PageMeta;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.AuthContext;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Pages;
import com.prism.shared.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
public class ModerationController {

    private final ModerationReportService reports;
    private final ModerationJobService jobs;
    private final RevisionRepository revisions;

    public ModerationController(
            ModerationReportService reports,
            ModerationJobService jobs,
            RevisionRepository revisions
    ) {
        this.reports = reports;
        this.jobs = jobs;
        this.revisions = revisions;
    }

    @GetMapping("/moderation/summary")
    public ApiResponse<?> summary() {
        return ApiResponse.ok(reports.platformSummary());
    }

    @GetMapping("/moderation/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> audit(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Pageable pageable = Pages.of(page, size, "createdAt,desc", "createdAt", Set.of("createdAt"));
        var result = reports.auditPage(pageable);
        return ApiResponse.page(result.getContent(), PageMeta.of(result));
    }

    @GetMapping("/events/{eventId}/moderation")
    public ApiResponse<?> byEvent(@PathVariable UUID eventId, @RequestParam(required = false) UUID revisionId) {
        EventRevision revision = resolveRevision(eventId, revisionId);
        WebsiteModerationReport report = reports.requireByRevision(revision.id);
        return ApiResponse.ok(reports.viewReport(report));
    }

    @GetMapping("/events/{eventId}/moderation/findings")
    public ApiResponse<?> findings(@PathVariable UUID eventId, @RequestParam(required = false) UUID revisionId) {
        EventRevision revision = resolveRevision(eventId, revisionId);
        WebsiteModerationReport report = reports.requireByRevision(revision.id);
        return ApiResponse.ok(reports.viewReport(report).get("findings"));
    }

    @PostMapping("/events/{eventId}/moderation/retry")
    public ApiResponse<?> retry(@PathVariable UUID eventId, @RequestParam(required = false) UUID revisionId, HttpServletRequest http) {
        UserPrincipal actor = AuthContext.require();
        EventRevision revision = resolveRevision(eventId, revisionId);
        WebsiteModerationReport report = reports.retry(revision.id, actor, Requests.clientIp(http));
        jobs.schedule(report.id);
        return ApiResponse.ok(reports.viewReport(report));
    }

    @GetMapping("/moderation/reports/{reportId}")
    public ApiResponse<?> report(@PathVariable UUID reportId) {
        return ApiResponse.ok(reports.viewReport(reports.require(reportId)));
    }

    @GetMapping("/moderation/reports/{reportId}/audit")
    public ApiResponse<?> reportAudit(@PathVariable UUID reportId) {
        reports.require(reportId);
        return ApiResponse.ok(reports.auditForReport(reportId));
    }

    @PatchMapping("/moderation/findings/{findingId}/status")
    public ApiResponse<?> findingStatus(
            @PathVariable UUID findingId,
            @RequestBody StatusRequest request,
            HttpServletRequest http
    ) {
        UserPrincipal actor = AuthContext.require();
        WebsiteModerationFinding finding = reports.updateFindingStatus(
                findingId,
                request.status(),
                request.note(),
                actor,
                Requests.clientIp(http)
        );
        return ApiResponse.ok(reports.viewFinding(finding));
    }

    @PostMapping("/moderation/findings/{findingId}/notes")
    public ApiResponse<?> findingNote(
            @PathVariable UUID findingId,
            @RequestBody NoteRequest request,
            HttpServletRequest http
    ) {
        UserPrincipal actor = AuthContext.require();
        WebsiteModerationFinding finding = reports.addFindingNote(findingId, request.note(), actor, Requests.clientIp(http));
        return ApiResponse.ok(reports.viewFinding(finding));
    }

    private EventRevision resolveRevision(UUID eventId, UUID revisionId) {
        if (revisionId != null) {
            EventRevision revision = revisions.findById(revisionId)
                    .orElseThrow(() -> ApiException.notFound("That revision was not found."));
            if (!revision.eventId.equals(eventId)) {
                throw ApiException.notFound("That revision was not found.");
            }
            return revision;
        }
        return revisions.findFirstByEventIdOrderByRevisionNumberDesc(eventId)
                .orElseThrow(() -> ApiException.notFound("That event site has no revision."));
    }

    public record StatusRequest(@NotBlank String status, String note) {
    }

    public record NoteRequest(@NotBlank String note) {
    }
}

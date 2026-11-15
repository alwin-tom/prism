package com.prism.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prism.revision.EventRevision;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ModerationReportService {

    private static final Set<String> FINDING_STATUSES = Set.of(
            "PENDING", "CONFIRMED", "FALSE_POSITIVE", "RESOLVED", "DISMISSED"
    );

    private final WebsiteModerationReportRepository reports;
    private final WebsiteModerationFindingRepository findings;
    private final ModerationAuditLogRepository audits;
    private final ModerationRiskAssessmentService risk;
    private final ObjectMapper mapper;

    public ModerationReportService(
            WebsiteModerationReportRepository reports,
            WebsiteModerationFindingRepository findings,
            ModerationAuditLogRepository audits,
            ModerationRiskAssessmentService risk,
            ObjectMapper mapper
    ) {
        this.reports = reports;
        this.findings = findings;
        this.audits = audits;
        this.risk = risk;
        this.mapper = mapper;
    }

    @Transactional
    public WebsiteModerationReport createPending(EventRevision revision) {
        return reports.findByRevisionId(revision.id).orElseGet(() -> {
            WebsiteModerationReport report = new WebsiteModerationReport();
            report.eventId = revision.eventId;
            report.revisionId = revision.id;
            report.revisionNumber = revision.revisionNumber;
            report.status = "PENDING";
            report.overallRisk = "CLEAN";
            report.summaryJson = emptySummary();
            report.provider = "openrouter";
            report.attemptCount = 0;
            report.maxAttempts = 3;
            return reports.save(report);
        });
    }

    @Transactional(readOnly = true)
    public WebsiteModerationReport requireByRevision(UUID revisionId) {
        return reports.findByRevisionId(revisionId)
                .orElseThrow(() -> ApiException.notFound("No moderation report exists for this submission."));
    }

    @Transactional(readOnly = true)
    public WebsiteModerationReport require(UUID reportId) {
        return reports.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("That moderation report was not found."));
    }

    @Transactional(readOnly = true)
    public WebsiteModerationFinding requireFinding(UUID findingId) {
        return findings.findById(findingId)
                .orElseThrow(() -> ApiException.notFound("That moderation finding was not found."));
    }

    public boolean isReadyForDecision(WebsiteModerationReport report) {
        return report != null && (
                "COMPLETED".equals(report.status)
                        || "FAILED".equals(report.status)
                        || "RETRY_REQUIRED".equals(report.status)
        );
    }

    public boolean blocksApproval(WebsiteModerationReport report) {
        if (report == null) {
            return true;
        }
        return "PENDING".equals(report.status) || "PROCESSING".equals(report.status);
    }

    @Transactional
    public void markProcessing(WebsiteModerationReport report) {
        report.status = "PROCESSING";
        report.attemptCount = report.attemptCount + 1;
        if (report.startedAt == null) {
            report.startedAt = Instant.now();
        }
        report.errorDetails = null;
        reports.save(report);
    }

    @Transactional
    public void markCompleted(WebsiteModerationReport report, ModerationAnalysisResult result, long durationMs) {
        findings.deleteByReportId(report.id);
        for (ModerationFindingDraft draft : result.findings()) {
            WebsiteModerationFinding finding = new WebsiteModerationFinding();
            finding.reportId = report.id;
            finding.category = draft.category();
            finding.severity = draft.severity();
            finding.confidence = draft.confidence() == null ? BigDecimal.ZERO : draft.confidence();
            finding.pageId = draft.pageId();
            finding.sectionId = draft.sectionId();
            finding.elementId = draft.elementId();
            finding.elementType = draft.elementType();
            finding.matchedText = draft.matchedText();
            finding.startOffset = draft.startOffset();
            finding.endOffset = draft.endOffset();
            finding.contextText = draft.context();
            finding.reason = draft.reason();
            finding.suggestion = draft.suggestion();
            finding.reviewStatus = "PENDING";
            findings.save(finding);
        }
        report.status = "COMPLETED";
        report.overallRisk = result.overallRisk();
        report.summaryJson = risk.summaryJson(result);
        report.explanation = result.explanation();
        report.modelId = result.modelId();
        report.provider = result.provider() == null ? "openrouter" : result.provider();
        report.completedAt = Instant.now();
        report.durationMs = durationMs;
        report.errorDetails = null;
        reports.save(report);
    }

    @Transactional
    public void markFailed(WebsiteModerationReport report, String error, long durationMs) {
        boolean retryable = report.attemptCount < report.maxAttempts;
        report.status = retryable ? "RETRY_REQUIRED" : "FAILED";
        report.errorDetails = clip(error, 2000);
        report.durationMs = durationMs;
        if (!retryable) {
            report.completedAt = Instant.now();
            report.overallRisk = "CLEAN".equals(report.overallRisk) ? "CLEAN" : report.overallRisk;
            if (report.summaryJson == null) {
                report.summaryJson = emptySummary();
            }
            ObjectNode summary = report.summaryJson.deepCopy();
            summary.put("clean", false);
            report.summaryJson = summary;
        }
        reports.save(report);
    }

    @Transactional
    public WebsiteModerationReport retry(UUID revisionId, UserPrincipal actor, String ip) {
        WebsiteModerationReport report = requireByRevision(revisionId);
        if (!"FAILED".equals(report.status) && !"RETRY_REQUIRED".equals(report.status) && !"COMPLETED".equals(report.status)) {
            if ("PROCESSING".equals(report.status) || "PENDING".equals(report.status)) {
                throw ApiException.conflict("Moderation is already queued or running.");
            }
        }
        String previous = report.status;
        report.status = "PENDING";
        report.errorDetails = null;
        report.completedAt = null;
        report.durationMs = null;
        if (report.attemptCount >= report.maxAttempts) {
            report.maxAttempts = report.attemptCount + 2;
        }
        reports.save(report);
        audit(report.id, null, actor.id(), "MODERATION_RETRY", previous, "PENDING", "Administrator requested moderation retry.", ip);
        return report;
    }

    @Transactional
    public WebsiteModerationFinding updateFindingStatus(UUID findingId, String status, String note, UserPrincipal actor, String ip) {
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!FINDING_STATUSES.contains(next)) {
            throw ApiException.badRequest("Choose a valid finding review status.");
        }
        WebsiteModerationFinding finding = requireFinding(findingId);
        String previous = finding.reviewStatus;
        finding.reviewStatus = next;
        if (note != null && !note.isBlank()) {
            finding.administratorNote = note.trim();
        }
        findings.save(finding);
        audit(finding.reportId, finding.id, actor.id(), "FINDING_STATUS", previous, next, note, ip);
        return finding;
    }

    @Transactional
    public WebsiteModerationFinding addFindingNote(UUID findingId, String note, UserPrincipal actor, String ip) {
        if (note == null || note.trim().length() < 2) {
            throw ApiException.badRequest("Write a short internal note.");
        }
        WebsiteModerationFinding finding = requireFinding(findingId);
        String previous = finding.administratorNote;
        finding.administratorNote = note.trim();
        findings.save(finding);
        audit(finding.reportId, finding.id, actor.id(), "FINDING_NOTE", previous, finding.administratorNote, note, ip);
        return finding;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> viewReport(WebsiteModerationReport report) {
        List<WebsiteModerationFinding> rows = findings.findByReportIdOrderBySeverityAscCreatedAtAsc(report.id);
        rows = new ArrayList<>(rows);
        rows.sort(Comparator
                .comparingInt((WebsiteModerationFinding item) -> severityRank(item.severity)).reversed()
                .thenComparing(item -> item.createdAt));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", report.id);
        body.put("eventId", report.eventId);
        body.put("revisionId", report.revisionId);
        body.put("revisionNumber", report.revisionNumber);
        body.put("status", report.status);
        body.put("overallRisk", report.overallRisk);
        body.put("summary", risk.summaryMap(report));
        body.put("explanation", report.explanation);
        body.put("provider", report.provider);
        body.put("model", report.modelId);
        body.put("attemptCount", report.attemptCount);
        body.put("maxAttempts", report.maxAttempts);
        body.put("startedAt", report.startedAt);
        body.put("completedAt", report.completedAt);
        body.put("durationMs", report.durationMs);
        body.put("errorDetails", report.errorDetails);
        body.put("createdAt", report.createdAt);
        body.put("updatedAt", report.updatedAt);
        body.put("findings", rows.stream().map(this::viewFinding).toList());
        body.put("readyForDecision", isReadyForDecision(report));
        body.put("blocksApproval", blocksApproval(report));
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> viewFinding(WebsiteModerationFinding finding) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", finding.id);
        body.put("reportId", finding.reportId);
        body.put("category", finding.category);
        body.put("severity", finding.severity);
        body.put("confidence", finding.confidence);
        body.put("pageId", finding.pageId);
        body.put("sectionId", finding.sectionId);
        body.put("elementId", finding.elementId);
        body.put("elementType", finding.elementType);
        body.put("matchedText", finding.matchedText);
        body.put("startOffset", finding.startOffset);
        body.put("endOffset", finding.endOffset);
        body.put("context", finding.contextText);
        body.put("reason", finding.reason);
        body.put("suggestion", finding.suggestion);
        body.put("reviewStatus", finding.reviewStatus);
        body.put("administratorNote", finding.administratorNote);
        body.put("createdAt", finding.createdAt);
        body.put("updatedAt", finding.updatedAt);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summaryCard(UUID revisionId) {
        return reports.findByRevisionId(revisionId).map(report -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reportId", report.id);
            body.put("status", report.status);
            body.put("overallRisk", report.overallRisk);
            body.put("summary", risk.summaryMap(report));
            body.put("explanation", report.explanation);
            body.put("model", report.modelId);
            body.put("provider", report.provider);
            body.put("completedAt", report.completedAt);
            body.put("errorDetails", report.errorDetails);
            body.put("readyForDecision", isReadyForDecision(report));
            body.put("blocksApproval", blocksApproval(report));
            return body;
        }).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> platformSummary() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pending", reports.countByStatus("PENDING"));
        body.put("processing", reports.countByStatus("PROCESSING"));
        body.put("completed", reports.countByStatus("COMPLETED"));
        body.put("failed", reports.countByStatus("FAILED"));
        body.put("retryRequired", reports.countByStatus("RETRY_REQUIRED"));
        body.put("critical", reports.countByOverallRiskAndStatus("CRITICAL", "COMPLETED"));
        body.put("high", reports.countByOverallRiskAndStatus("HIGH", "COMPLETED"));
        body.put("medium", reports.countByOverallRiskAndStatus("MEDIUM", "COMPLETED"));
        body.put("low", reports.countByOverallRiskAndStatus("LOW", "COMPLETED"));
        body.put("clean", reports.countByOverallRiskAndStatus("CLEAN", "COMPLETED"));
        return body;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> auditPage(Pageable pageable) {
        return audits.findAllByOrderByCreatedAtDesc(pageable).map(this::viewAudit);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditForReport(UUID reportId) {
        return audits.findByReportIdOrderByCreatedAtDesc(reportId).stream().map(this::viewAudit).toList();
    }

    private void audit(UUID reportId, UUID findingId, UUID adminId, String action, String previous, String next, String note, String ip) {
        ModerationAuditLog row = new ModerationAuditLog();
        row.reportId = reportId;
        row.findingId = findingId;
        row.administratorId = adminId;
        row.action = action;
        row.previousValue = clip(previous, 2000);
        row.newValue = clip(next, 2000);
        row.note = clip(note, 2000);
        audits.save(row);
    }

    private Map<String, Object> viewAudit(ModerationAuditLog row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.id);
        body.put("reportId", row.reportId);
        body.put("findingId", row.findingId);
        body.put("administratorId", row.administratorId);
        body.put("action", row.action);
        body.put("previousValue", row.previousValue);
        body.put("newValue", row.newValue);
        body.put("note", row.note);
        body.put("createdAt", row.createdAt);
        return body;
    }

    private ObjectNode emptySummary() {
        ObjectNode summary = mapper.createObjectNode();
        summary.put("critical", 0);
        summary.put("high", 0);
        summary.put("medium", 0);
        summary.put("low", 0);
        summary.put("clean", false);
        return summary;
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}

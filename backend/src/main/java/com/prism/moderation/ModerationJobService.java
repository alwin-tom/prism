package com.prism.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.editor.EditorDocumentRepository;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ModerationJobService {

    private static final Logger log = LoggerFactory.getLogger(ModerationJobService.class);

    private final ModerationReportService reports;
    private final ContentModerationService moderation;
    private final RevisionRepository revisions;
    private final EditorDocumentRepository editorDocuments;
    private final WebsiteModerationReportRepository reportRepository;
    private final Executor moderationExecutor;
    private final TransactionTemplate transactions;

    public ModerationJobService(
            ModerationReportService reports,
            ContentModerationService moderation,
            RevisionRepository revisions,
            EditorDocumentRepository editorDocuments,
            WebsiteModerationReportRepository reportRepository,
            @Qualifier("moderationExecutor") Executor moderationExecutor,
            TransactionTemplate transactions
    ) {
        this.reports = reports;
        this.moderation = moderation;
        this.revisions = revisions;
        this.editorDocuments = editorDocuments;
        this.reportRepository = reportRepository;
        this.moderationExecutor = moderationExecutor;
        this.transactions = transactions;
    }

    @Transactional
    public WebsiteModerationReport enqueue(EventRevision revision) {
        WebsiteModerationReport report = reports.createPending(revision);
        schedule(report.id);
        return report;
    }

    public void schedule(UUID reportId) {
        try {
            moderationExecutor.execute(() -> process(reportId));
        } catch (RejectedExecutionException exception) {
            log.warn("Moderation executor is full; report {} will wait for the scheduler", reportId);
        }
    }

    @Transactional
    public void recoverInterrupted() {
        Instant stale = Instant.now().minusSeconds(15 * 60);
        for (WebsiteModerationReport report : reportRepository.findByStatusInOrderByUpdatedAtAsc(List.of("PROCESSING"))) {
            if (report.updatedAt != null && report.updatedAt.isBefore(stale)) {
                report.status = "RETRY_REQUIRED";
                report.errorDetails = "Moderation was interrupted and will be retried.";
                reportRepository.save(report);
            }
        }
        for (WebsiteModerationReport report : reportRepository.findByStatusInOrderByUpdatedAtAsc(List.of("PENDING", "RETRY_REQUIRED"))) {
            schedule(report.id);
        }
    }

    public void process(UUID reportId) {
        WebsiteModerationReport locked = transactions.execute(status -> {
            WebsiteModerationReport report = reportRepository.findById(reportId).orElse(null);
            if (report == null) {
                return null;
            }
            if (!"PENDING".equals(report.status) && !"RETRY_REQUIRED".equals(report.status)) {
                return null;
            }
            reports.markProcessing(report);
            return reportRepository.findById(reportId).orElse(report);
        });
        if (locked == null) {
            return;
        }
        long started = System.nanoTime();
        try {
            EventRevision revision = revisions.findById(locked.revisionId)
                    .orElseThrow(() -> new IllegalStateException("Submission revision was not found."));
            JsonNode document = editorDocuments.findByRevisionId(revision.id)
                    .map(row -> row.documentJson)
                    .orElse(null);
            ModerationAnalysisResult result = moderation.analyse(document, revision.configurationJson);
            long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            reports.markCompleted(locked, result, duration);
        } catch (RuntimeException exception) {
            long duration = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            log.error("Content moderation failed for report {}: {}", reportId, message);
            WebsiteModerationReport current = reportRepository.findById(reportId).orElse(locked);
            reports.markFailed(current, message, duration);
        }
    }
}

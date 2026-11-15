package com.prism.review;

import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.hosting.HostingService;
import com.prism.moderation.ModerationJobService;
import com.prism.moderation.ModerationReportService;
import com.prism.moderation.WebsiteModerationReport;
import com.prism.moderation.WebsiteModerationReportRepository;
import com.prism.notification.OutboxService;
import com.prism.publishing.PublishingService;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.audit.AuditService;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewService {

    private final EventRepository events;
    private final EventService eventService;
    private final RevisionRepository revisions;
    private final ReviewRepository reviews;
    private final HostingService hosting;
    private final PublishingService publishing;
    private final OutboxService outbox;
    private final AuditService audit;
    private final UserRepository users;
    private final WebsiteModerationReportRepository moderationReports;
    private final ModerationReportService moderation;
    private final ModerationJobService moderationJobs;

    public ReviewService(
            EventRepository events,
            EventService eventService,
            RevisionRepository revisions,
            ReviewRepository reviews,
            HostingService hosting,
            PublishingService publishing,
            OutboxService outbox,
            AuditService audit,
            UserRepository users,
            WebsiteModerationReportRepository moderationReports,
            ModerationReportService moderation,
            ModerationJobService moderationJobs
    ) {
        this.events = events;
        this.eventService = eventService;
        this.revisions = revisions;
        this.reviews = reviews;
        this.hosting = hosting;
        this.publishing = publishing;
        this.outbox = outbox;
        this.audit = audit;
        this.users = users;
        this.moderationReports = moderationReports;
        this.moderation = moderation;
        this.moderationJobs = moderationJobs;
    }

    @Transactional
    public Map<String, Object> open(UUID eventId, UserPrincipal reviewer) {
        EventSite event = lock(eventId);
        rejectSelfReview(event, reviewer);
        EventRevision revision = currentQueue(event.id);
        if (revision != null && "SUBMITTED".equals(revision.submissionStatus)) {
            revision.submissionStatus = "IN_REVIEW";
            revisions.save(revision);
            if (!"PUBLISHED".equals(event.status) && !"SUSPENDED".equals(event.status)) {
                event.status = "IN_REVIEW";
                events.save(event);
            }
        }
        EventRevision shown = revision == null ? eventService.latest(event.id) : revision;
        if (shown != null && moderationReports.findByRevisionId(shown.id).isEmpty()
                && ("SUBMITTED".equals(shown.submissionStatus) || "IN_REVIEW".equals(shown.submissionStatus))) {
            moderationJobs.enqueue(shown);
        }
        return eventService.present(event, shown);
    }

    @Transactional
    public Map<String, Object> decide(UUID eventId, UserPrincipal reviewer, String decision, String comments, String ip) {
        EventSite event = lock(eventId);
        rejectSelfReview(event, reviewer);
        EventRevision revision = currentQueue(event.id);
        if (revision == null) {
            throw ApiException.conflict("There is no submission waiting on a reviewer.");
        }
        WebsiteModerationReport report = moderationReports.findByRevisionId(revision.id).orElse(null);
        if (moderation.blocksApproval(report)) {
            throw ApiException.conflict("Wait for AI content moderation to finish before recording a review decision.");
        }
        if ("APPROVED".equals(decision) && (report == null || !"COMPLETED".equals(report.status))) {
            throw ApiException.conflict("Approval requires a completed moderation report. Retry moderation or request changes.");
        }
        String note = comments == null ? "" : comments.trim();
        if (!"APPROVED".equals(decision) && note.length() < 3) {
            throw ApiException.badRequest("Write a short note for the customer.");
        }
        Instant now = Instant.now();
        revision.submissionStatus = decision;
        if ("APPROVED".equals(decision)) {
            revision.approvedAt = now;
            revision.approvedBy = reviewer.id();
            event.approvedRevisionId = revision.id;
        }
        revisions.save(revision);
        EventReview review = new EventReview();
        review.eventId = event.id;
        review.revisionId = revision.id;
        review.reviewerId = reviewer.id();
        review.decision = decision;
        review.comments = note;
        reviews.save(review);
        UserAccount owner = users.findById(event.userId).orElse(null);
        String phone = owner == null ? "" : owner.phoneNumber;
        String name = owner == null ? "" : owner.fullName;
        if ("APPROVED".equals(decision)) {
            if ("SUSPENDED".equals(event.status)) {
                events.save(event);
            } else if (hosting.covers(event.id, now)) {
                publishing.publish(event, revision.id, now);
            } else if (!"PUBLISHED".equals(event.status)) {
                event.status = "APPROVED";
                events.save(event);
            } else {
                events.save(event);
            }
            outbox.enqueue("EVENT", event.id, "APPROVAL_NOTIFICATION", Map.of(
                    "phone", phone, "name", name, "eventName", event.eventName, "slug", event.slug
            ), "approved:" + revision.id);
            audit.record(reviewer.id(), "REVIEW_APPROVED", "EVENT", event.id, Map.of("revision", revision.revisionNumber), ip);
        } else if ("REJECTED".equals(decision)) {
            if (!"PUBLISHED".equals(event.status) && !"SUSPENDED".equals(event.status) && !"EXPIRED".equals(event.status)) {
                event.status = "REJECTED";
                events.save(event);
            }
            outbox.enqueue("EVENT", event.id, "REJECTION_NOTIFICATION", Map.of(
                    "phone", phone, "name", name, "eventName", event.eventName, "comments", note
            ), "rejected:" + revision.id);
            audit.record(reviewer.id(), "REVIEW_REJECTED", "EVENT", event.id, Map.of(), ip);
        } else if ("CHANGES_REQUESTED".equals(decision)) {
            if (!"PUBLISHED".equals(event.status) && !"SUSPENDED".equals(event.status) && !"EXPIRED".equals(event.status)) {
                event.status = "CHANGES_REQUESTED";
                events.save(event);
            }
            outbox.enqueue("EVENT", event.id, "CHANGES_REQUESTED", Map.of(
                    "phone", phone, "name", name, "eventName", event.eventName, "comments", note
            ), "changes:" + revision.id);
            audit.record(reviewer.id(), "CHANGES_REQUESTED", "EVENT", event.id, Map.of(), ip);
        } else {
            throw ApiException.badRequest("Choose approve, reject, or request changes.");
        }
        return eventService.present(event, revision);
    }

    private EventSite lock(UUID eventId) {
        return events.lockById(eventId).orElseThrow(() -> ApiException.notFound("That event site was not found."));
    }

    private void rejectSelfReview(EventSite event, UserPrincipal reviewer) {
        if (event.userId.equals(reviewer.id())) {
            throw ApiException.forbidden("You cannot review your own site.");
        }
        if (!reviewer.isStaff()) {
            throw ApiException.forbidden("You do not have access to this.");
        }
    }

    private EventRevision currentQueue(UUID eventId) {
        return revisions.findByEventIdOrderByRevisionNumberAsc(eventId).stream()
                .filter(revision -> "SUBMITTED".equals(revision.submissionStatus) || "IN_REVIEW".equals(revision.submissionStatus))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}

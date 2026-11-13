package com.prism.event;

import com.prism.analytics.EventViewRepository;
import com.prism.creation.WebsiteCreationSessionRepository;
import com.prism.creation.WebsiteGenerationJobRepository;
import com.prism.editor.EditorDocumentRepository;
import com.prism.editor.EditorVersionRepository;
import com.prism.hosting.HostingExtensionRepository;
import com.prism.hosting.HostingPeriodRepository;
import com.prism.media.EventMedia;
import com.prism.media.MediaRepository;
import com.prism.media.MediaService;
import com.prism.payment.PaymentRepository;
import com.prism.publishing.PublicationRepository;
import com.prism.review.ReviewRepository;
import com.prism.revision.RevisionRepository;
import com.prism.rsvp.RsvpRepository;
import com.prism.shared.audit.AuditService;
import com.prism.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EventDeletionService {

    private static final Logger log = LoggerFactory.getLogger(EventDeletionService.class);

    private final EventRepository events;
    private final RevisionRepository revisions;
    private final UrlReservationRepository reservations;
    private final MediaRepository media;
    private final MediaService files;
    private final ReviewRepository reviews;
    private final PublicationRepository publications;
    private final PaymentRepository payments;
    private final HostingPeriodRepository hostingPeriods;
    private final HostingExtensionRepository hostingExtensions;
    private final RsvpRepository rsvps;
    private final EventViewRepository views;
    private final EditorDocumentRepository documents;
    private final EditorVersionRepository documentVersions;
    private final WebsiteCreationSessionRepository sessions;
    private final WebsiteGenerationJobRepository jobs;
    private final AuditService audit;

    public EventDeletionService(
            EventRepository events,
            RevisionRepository revisions,
            UrlReservationRepository reservations,
            MediaRepository media,
            MediaService files,
            ReviewRepository reviews,
            PublicationRepository publications,
            PaymentRepository payments,
            HostingPeriodRepository hostingPeriods,
            HostingExtensionRepository hostingExtensions,
            RsvpRepository rsvps,
            EventViewRepository views,
            EditorDocumentRepository documents,
            EditorVersionRepository documentVersions,
            WebsiteCreationSessionRepository sessions,
            WebsiteGenerationJobRepository jobs,
            AuditService audit
    ) {
        this.events = events;
        this.revisions = revisions;
        this.reservations = reservations;
        this.media = media;
        this.files = files;
        this.reviews = reviews;
        this.publications = publications;
        this.payments = payments;
        this.hostingPeriods = hostingPeriods;
        this.hostingExtensions = hostingExtensions;
        this.rsvps = rsvps;
        this.views = views;
        this.documents = documents;
        this.documentVersions = documentVersions;
        this.sessions = sessions;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional
    public void delete(EventSite event, UserPrincipal actor, String ip) {
        UUID id = event.id;
        List<EventMedia> stored = media.findByEventIdOrderByCreatedAtAsc(id);
        List<UUID> sessionIds = sessions.findByEventId(id).stream().map(session -> session.id).toList();
        Instant now = Instant.now();
        jobs.cancelOpenForEvent(id, now);
        if (sessionIds.isEmpty()) {
            jobs.detachEvent(id);
        } else {
            jobs.detachEvent(id, sessionIds);
        }
        sessions.deleteByEventId(id);
        documentVersions.deleteByEventId(id);
        documents.deleteByEventId(id);
        reviews.deleteByEventId(id);
        publications.deleteByEventId(id);
        media.deleteByEventId(id);
        hostingExtensions.deleteByEventId(id);
        hostingPeriods.deleteByEventId(id);
        payments.deleteByEventId(id);
        rsvps.deleteByEventId(id);
        views.deleteByEventId(id);
        reservations.deleteByEventId(id);
        events.clearRevisionPointers(id);
        revisions.deleteByEventId(id);
        events.deleteById(id);
        audit.record(actor.id(), "EVENT_DELETED", "EVENT", id, Map.of("slug", event.slug), ip);
        for (EventMedia row : stored) {
            try {
                files.deleteFiles(row);
            } catch (IOException exception) {
                log.warn("A file for deleted website {} stayed on disk", id, exception);
            }
        }
    }
}

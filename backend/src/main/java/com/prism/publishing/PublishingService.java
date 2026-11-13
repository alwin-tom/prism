package com.prism.publishing;

import com.prism.event.EventRepository;
import com.prism.event.EventSite;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PublishingService {

    private final PublicationRepository publications;
    private final EventRepository events;
    private final RevisionRepository revisions;

    public PublishingService(PublicationRepository publications, EventRepository events, RevisionRepository revisions) {
        this.publications = publications;
        this.events = events;
        this.revisions = revisions;
    }

    @Transactional
    public void publish(EventSite event, UUID revisionId, Instant at) {
        EventRevision revision = revisions.findById(revisionId).orElseThrow();
        publications.findFirstByEventIdAndPublicationStatus(event.id, "PUBLISHED").ifPresent(current -> {
            current.publicationStatus = "UNPUBLISHED";
            current.unpublishedAt = at;
            publications.save(current);
        });
        publications.flush();
        EventPublication publication = new EventPublication();
        publication.eventId = event.id;
        publication.revisionId = revision.id;
        publication.publishedAt = at;
        publication.publicationStatus = "PUBLISHED";
        publications.save(publication);
        event.publishedRevisionId = revision.id;
        event.status = "PUBLISHED";
        events.save(event);
    }

    @Transactional
    public void markExpired(EventSite event, Instant at) {
        if (!"PUBLISHED".equals(event.status) && !"SUSPENDED".equals(event.status)) {
            return;
        }
        publications.findFirstByEventIdAndPublicationStatus(event.id, "PUBLISHED").ifPresent(current -> {
            current.publicationStatus = "EXPIRED";
            current.unpublishedAt = at;
            publications.save(current);
        });
        event.status = "EXPIRED";
        events.save(event);
    }

    @Transactional
    public void unpublish(EventSite event, Instant at) {
        publications.findFirstByEventIdAndPublicationStatus(event.id, "PUBLISHED").ifPresent(current -> {
            current.publicationStatus = "UNPUBLISHED";
            current.unpublishedAt = at;
            publications.save(current);
        });
        if ("PUBLISHED".equals(event.status) || "SUSPENDED".equals(event.status)) {
            event.status = "APPROVED";
            events.save(event);
        }
    }
}

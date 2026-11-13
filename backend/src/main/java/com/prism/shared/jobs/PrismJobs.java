package com.prism.shared.jobs;

import com.prism.event.EventRepository;
import com.prism.event.EventSite;
import com.prism.hosting.HostingPeriod;
import com.prism.hosting.HostingService;
import com.prism.media.EventMedia;
import com.prism.media.MediaRepository;
import com.prism.media.MediaService;
import com.prism.notification.OutboxProcessor;
import com.prism.notification.OutboxService;
import com.prism.payment.PaymentService;
import com.prism.publishing.PublishingService;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "prism.scheduling", name = "enabled", matchIfMissing = true)
public class PrismJobs {

    private static final Logger log = LoggerFactory.getLogger(PrismJobs.class);

    private final AdvisoryLock locks;
    private final HostingService hosting;
    private final EventRepository events;
    private final PublishingService publishing;
    private final OutboxService outbox;
    private final OutboxProcessor outboxProcessor;
    private final PaymentService payments;
    private final MediaRepository media;
    private final MediaService mediaService;
    private final UserRepository users;
    private final com.prism.shared.config.PrismProperties properties;

    public PrismJobs(
            AdvisoryLock locks,
            HostingService hosting,
            EventRepository events,
            PublishingService publishing,
            OutboxService outbox,
            OutboxProcessor outboxProcessor,
            PaymentService payments,
            MediaRepository media,
            MediaService mediaService,
            UserRepository users,
            com.prism.shared.config.PrismProperties properties
    ) {
        this.locks = locks;
        this.hosting = hosting;
        this.events = events;
        this.publishing = publishing;
        this.outbox = outbox;
        this.outboxProcessor = outboxProcessor;
        this.payments = payments;
        this.media = media;
        this.mediaService = mediaService;
        this.users = users;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void maintenance() {
        run(8142001L, this::expireAndRemind);
        run(8142002L, () -> payments.expirePending(Instant.now().minus(2, ChronoUnit.HOURS)));
        run(8142003L, () -> outboxProcessor.processDue(Instant.now()));
        run(8142004L, this::cleanupMedia);
    }

    public void expireAndRemind() {
        Instant now = Instant.now();
        List<java.util.UUID> expired = hosting.expireDue(now);
        for (java.util.UUID eventId : expired) {
            events.findById(eventId).ifPresent(event -> {
                publishing.markExpired(event, now);
                notify(event, "HOSTING_EXPIRY", "expired:" + event.id + ":" + now.truncatedTo(ChronoUnit.DAYS), "Hosting for " + event.eventName + " has ended.");
            });
        }
        for (HostingPeriod period : hosting.endingBetween(now, now.plus(1, ChronoUnit.DAYS))) {
            events.findById(period.eventId).ifPresent(event ->
                    notify(event, "HOSTING_EXPIRY", "reminder-1:" + period.id, "Hosting for " + event.eventName + " ends within a day."));
        }
        for (HostingPeriod period : hosting.endingBetween(now.plus(6, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS))) {
            events.findById(period.eventId).ifPresent(event ->
                    notify(event, "HOSTING_EXPIRY", "reminder-7:" + period.id, "Hosting for " + event.eventName + " ends within a week."));
        }
    }

    private void cleanupMedia() {
        Instant now = Instant.now();
        Instant retention = now.minus(properties.getMedia().getRetentionDaysAfterExpiry(), ChronoUnit.DAYS);
        for (EventSite event : events.findAllByStatus("EXPIRED")) {
            HostingPeriod latest = hosting.latestOpen(event.id);
            boolean old = latest == null && event.updatedAt.isBefore(retention);
            if (!old) {
                continue;
            }
            purge(event);
        }
        Instant abandoned = now.minus(14, ChronoUnit.DAYS);
        for (EventSite event : events.findByStatusAndUpdatedAtBefore("DRAFT", abandoned)) {
            boolean neverSubmitted = true;
            purge(event);
            if (!neverSubmitted) {
                continue;
            }
        }
    }

    private void purge(EventSite event) {
        for (EventMedia row : media.findByEventIdOrderByCreatedAtAsc(event.id)) {
            try {
                mediaService.deleteFiles(row);
                row.storagePath = "purged";
                row.thumbnailPath = null;
                media.save(row);
            } catch (IOException exception) {
                log.warn("Could not remove media {}", row.id);
            }
        }
    }

    private void notify(EventSite event, String type, String dedupe, String detail) {
        UserAccount owner = users.findById(event.userId).orElse(null);
        if (owner == null) {
            return;
        }
        outbox.enqueue("EVENT", event.id, type, java.util.Map.of(
                "phone", owner.phoneNumber,
                "name", owner.fullName,
                "eventName", event.eventName,
                "detail", detail
        ), dedupe);
    }

    private void run(long key, Runnable job) {
        if (!locks.tryLock(key)) {
            return;
        }
        try {
            job.run();
        } catch (RuntimeException exception) {
            log.error("Scheduled WebAux job {} failed", key, exception);
        } finally {
            locks.unlock(key);
        }
    }
}

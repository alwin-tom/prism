package com.prism.hosting;

import com.prism.event.EventSite;
import com.prism.payment.Payment;
import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class HostingService {

    private final HostingPeriodRepository periods;
    private final HostingExtensionRepository extensions;

    public HostingService(HostingPeriodRepository periods, HostingExtensionRepository extensions) {
        this.periods = periods;
        this.extensions = extensions;
    }

    public List<HostingPeriod> forEvent(UUID eventId) {
        return periods.findByEventIdOrderByStartAtAsc(eventId);
    }

    public boolean covers(UUID eventId, Instant instant) {
        return forEvent(eventId).stream().anyMatch(period -> covers(period, instant));
    }

    public HostingPeriod latestOpen(UUID eventId) {
        return forEvent(eventId).stream()
                .filter(period -> "ACTIVE".equals(period.status) || "SCHEDULED".equals(period.status))
                .max(Comparator.comparing(period -> period.endAt))
                .orElse(null);
    }

    public int daysRemaining(UUID eventId, Instant now) {
        HostingPeriod latest = latestOpen(eventId);
        if (latest == null || !latest.endAt.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, latest.endAt).getSeconds();
        return (int) Math.max(1, (seconds + 86_399) / 86_400);
    }

    @Transactional
    public HostingPeriod openPeriod(EventSite event, Payment payment, int days, int dailyRatePaise, Instant now, boolean extension) {
        HostingPeriod latest = latestOpen(event.id);
        Instant start = now;
        String status = "ACTIVE";
        if (extension && latest != null && latest.endAt.isAfter(now)) {
            start = latest.endAt;
            status = "SCHEDULED";
        }
        HostingPeriod period = new HostingPeriod();
        period.eventId = event.id;
        period.paymentId = payment.id;
        period.startAt = start;
        period.endAt = start.plus(days, ChronoUnit.DAYS);
        period.days = days;
        period.dailyRatePaise = dailyRatePaise;
        period.status = status;
        periods.save(period);
        if (extension) {
            HostingExtension row = new HostingExtension();
            row.eventId = event.id;
            row.hostingPeriodId = period.id;
            row.paymentId = payment.id;
            row.additionalDays = days;
            row.amountPaise = payment.amountPaise;
            extensions.save(row);
        }
        return period;
    }

    @Transactional
    public List<UUID> expireDue(Instant now) {
        List<HostingPeriod> due = periods.findByStatusAndEndAtBefore("ACTIVE", now.plusMillis(1));
        java.util.ArrayList<UUID> expiredEvents = new java.util.ArrayList<>();
        for (HostingPeriod period : due) {
            if (period.endAt.isAfter(now)) {
                continue;
            }
            period.status = "EXPIRED";
            periods.save(period);
            boolean stillCovered = covers(period.eventId, now);
            if (!stillCovered) {
                expiredEvents.add(period.eventId);
            } else {
                forEvent(period.eventId).stream()
                        .filter(candidate -> "SCHEDULED".equals(candidate.status) && !candidate.startAt.isAfter(now) && candidate.endAt.isAfter(now))
                        .findFirst()
                        .ifPresent(next -> {
                            next.status = "ACTIVE";
                            periods.save(next);
                        });
            }
        }
        return expiredEvents;
    }

    public List<HostingPeriod> endingBetween(Instant from, Instant to) {
        return periods.findEndingBetween(from, to);
    }

    private static boolean covers(HostingPeriod period, Instant instant) {
        return ("ACTIVE".equals(period.status) || "SCHEDULED".equals(period.status))
                && !period.startAt.isAfter(instant)
                && period.endAt.isAfter(instant);
    }

    public HostingPeriod require(UUID id) {
        return periods.findById(id).orElseThrow(() -> ApiException.notFound("That hosting period was not found."));
    }
}

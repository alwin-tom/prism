package com.prism.payment;

import com.prism.event.EventRepository;
import com.prism.event.EventSite;
import com.prism.hosting.HostingPeriod;
import com.prism.hosting.HostingService;
import com.prism.notification.OutboxService;
import com.prism.publishing.PublishingService;
import com.prism.shared.audit.AuditService;
import com.prism.shared.exception.ApiException;
import com.prism.shared.util.Money;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentCompletionService {

    private final PaymentRepository payments;
    private final EventRepository events;
    private final HostingService hosting;
    private final PublishingService publishing;
    private final OutboxService outbox;
    private final AuditService audit;
    private final UserRepository users;

    public PaymentCompletionService(
            PaymentRepository payments,
            EventRepository events,
            HostingService hosting,
            PublishingService publishing,
            OutboxService outbox,
            AuditService audit,
            UserRepository users
    ) {
        this.payments = payments;
        this.events = events;
        this.hosting = hosting;
        this.publishing = publishing;
        this.outbox = outbox;
        this.audit = audit;
        this.users = users;
    }

    @Transactional
    public Payment complete(UUID paymentId, String gatewayPaymentId, String method, String ip) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("That payment was not found."));
        if (hosting.forEvent(payment.eventId).stream().anyMatch(period -> payment.id.equals(period.paymentId))) {
            return payment;
        }
        EventSite event = events.lockById(payment.eventId).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        Instant now = Instant.now();
        payment.status = "PAID";
        payment.verifiedAt = now;
        if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
            payment.gatewayPaymentId = gatewayPaymentId;
        }
        payments.save(payment);
        boolean extension = "HOSTING_EXTENSION".equals(payment.paymentType);
        HostingPeriod period = hosting.openPeriod(event, payment, payment.days, payment.dailyRatePaise, now, extension);
        UUID revisionId = extension && event.publishedRevisionId != null
                ? event.publishedRevisionId
                : event.approvedRevisionId;
        if (revisionId == null) {
            throw ApiException.badRequest("This site has no approved revision to publish.");
        }
        boolean activatesNow = !period.startAt.isAfter(now);
        if (activatesNow && !"SUSPENDED".equals(event.status)) {
            publishing.publish(event, revisionId, now);
        }
        UserAccount owner = users.findById(event.userId).orElse(null);
        String phone = owner == null ? "" : owner.phoneNumber;
        String name = owner == null ? "" : owner.fullName;
        audit.record(event.userId, "BYPASS".equals(method) ? "PAYMENT_BYPASS" : "PAYMENT_VERIFIED",
                "PAYMENT", payment.id, Map.of("method", method, "amountPaise", payment.amountPaise), ip);
        outbox.enqueue("PAYMENT", payment.id, "PAYMENT_CONFIRMATION", Map.of(
                "phone", phone,
                "name", name,
                "eventName", event.eventName,
                "amount", Money.formatPaise(payment.amountPaise),
                "slug", event.slug
        ), "payment:" + payment.id);
        if (activatesNow && !"SUSPENDED".equals(event.status)) {
            outbox.enqueue("EVENT", event.id, "PUBLISHING_CONFIRMATION", Map.of(
                    "phone", phone,
                    "name", name,
                    "eventName", event.eventName,
                    "slug", event.slug,
                    "days", period.days
            ), "published:" + payment.id);
        } else if (extension) {
            outbox.enqueue("EVENT", event.id, "HOSTING_EXTENSION_CONFIRMATION", Map.of(
                    "phone", phone,
                    "name", name,
                    "eventName", event.eventName,
                    "days", period.days,
                    "amount", Money.formatPaise(payment.amountPaise)
            ), "extension:" + payment.id);
        }
        return payment;
    }
}

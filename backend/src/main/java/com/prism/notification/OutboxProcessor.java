package com.prism.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.notification.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("REGISTRATION_CONFIRMATION", "prism_registration"),
            Map.entry("SUBMISSION_CONFIRMATION", "prism_submission"),
            Map.entry("APPROVAL_NOTIFICATION", "prism_approved"),
            Map.entry("CHANGES_REQUESTED", "prism_changes"),
            Map.entry("REJECTION_NOTIFICATION", "prism_rejected"),
            Map.entry("PAYMENT_CONFIRMATION", "prism_payment"),
            Map.entry("PUBLISHING_CONFIRMATION", "prism_published"),
            Map.entry("RSVP_NOTIFICATION", "prism_rsvp"),
            Map.entry("HOSTING_EXPIRY", "prism_hosting_expiry"),
            Map.entry("HOSTING_EXTENSION_CONFIRMATION", "prism_hosting_extended")
    );

    private final OutboxRepository outbox;
    private final WhatsappMessageRepository messages;
    private final WhatsAppService whatsApp;

    public OutboxProcessor(OutboxRepository outbox, WhatsappMessageRepository messages, WhatsAppService whatsApp) {
        this.outbox = outbox;
        this.messages = messages;
        this.whatsApp = whatsApp;
    }

    @Transactional
    public int processDue(Instant now) {
        List<OutboxEvent> due = outbox.findTop20ByStatusAndAvailableAtBeforeOrderByCreatedAtAsc("PENDING", now.plusSeconds(1));
        int processed = 0;
        for (OutboxEvent event : due) {
            deliver(event, now);
            processed++;
        }
        return processed;
    }

    private void deliver(OutboxEvent event, Instant now) {
        JsonNode payload = event.payload;
        String phone = payload.path("phone").asText("");
        String template = TEMPLATES.getOrDefault(event.eventType, "prism_notice");
        WhatsappMessage message = new WhatsappMessage();
        message.phoneNumber = phone.isBlank() ? "unknown" : phone;
        message.templateName = template;
        message.payload = payload;
        message.attempts = event.attempts + 1;
        if (!whatsApp.enabled() || phone.isBlank()) {
            message.status = "SKIPPED";
            messages.save(message);
            event.status = "PROCESSED";
            event.processedAt = now;
            outbox.save(event);
            return;
        }
        try {
            String id = whatsApp.sendTemplate(phone, template, List.of(
                    payload.path("name").asText(""),
                    payload.path("eventName").asText(payload.path("amount").asText(""))
            ));
            message.status = "SENT";
            message.providerMessageId = id;
            message.sentAt = now;
            messages.save(message);
            event.status = "PROCESSED";
            event.processedAt = now;
            event.attempts = message.attempts;
            outbox.save(event);
        } catch (RuntimeException exception) {
            log.warn("Outbox delivery failed for {}", event.eventType);
            message.status = "FAILED";
            message.lastError = exception.getMessage();
            messages.save(message);
            event.attempts = message.attempts;
            event.lastError = exception.getMessage();
            if (event.attempts >= 5) {
                event.status = "FAILED";
            } else {
                event.availableAt = now.plusSeconds(60L * event.attempts);
            }
            outbox.save(event);
        }
    }
}

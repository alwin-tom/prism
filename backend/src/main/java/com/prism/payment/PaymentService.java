package com.prism.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.event.EventRepository;
import com.prism.event.EventSite;
import com.prism.hosting.HostingService;
import com.prism.pricing.PricingService;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.audit.AuditService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentWebhookRepository webhooks;
    private final EventRepository events;
    private final RevisionRepository revisions;
    private final PricingService pricing;
    private final HostingService hosting;
    private final PrismProperties properties;
    private final RazorpayClient razorpay;
    private final PaymentCompletionService completion;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository payments,
            PaymentWebhookRepository webhooks,
            EventRepository events,
            RevisionRepository revisions,
            PricingService pricing,
            HostingService hosting,
            PrismProperties properties,
            RazorpayClient razorpay,
            PaymentCompletionService completion,
            AuditService audit,
            ObjectMapper objectMapper
    ) {
        this.payments = payments;
        this.webhooks = webhooks;
        this.events = events;
        this.revisions = revisions;
        this.pricing = pricing;
        this.hosting = hosting;
        this.properties = properties;
        this.razorpay = razorpay;
        this.completion = completion;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public PricingService.Quote quoteFor(EventSite event, String paymentType, Integer days) {
        int pricedDays = days == null ? event.requestedDays : days;
        return pricing.quote(pricedDays);
    }

    @Transactional
    public Map<String, Object> start(EventSite event, String paymentType, Integer days, String requestedMethod, String ip) {
        String type = normalizeType(event, paymentType);
        int pricedDays = days == null ? event.requestedDays : days;
        PricingService.Quote quote = pricing.quote(pricedDays);
        ensurePayable(event, type);
        boolean useBypass = properties.isPaymentBypassEnabled()
                && (!razorpay.enabled() || "BYPASS".equalsIgnoreCase(requestedMethod));
        if (!useBypass && !razorpay.enabled()) {
            throw ApiException.unavailable("Payments are not configured. Set up Razorpay or the development payment bypass.");
        }
        Payment payment = new Payment();
        payment.eventId = event.id;
        payment.paymentType = type;
        payment.amountPaise = quote.amountPaise();
        payment.days = quote.days();
        payment.dailyRatePaise = quote.dailyRatePaise();
        payment.currency = quote.currency();
        payment.createdAt = Instant.now();
        if (useBypass) {
            payment.gateway = "BYPASS";
            payment.status = "PENDING";
            payments.save(payment);
            Payment paid = completion.complete(payment.id, null, "BYPASS", ip);
            Map<String, Object> body = view(paid);
            body.put("bypass", true);
            return body;
        }
        payment.gateway = "RAZORPAY";
        payment.status = "PENDING";
        payments.save(payment);
        String receipt = payment.id.toString().replace("-", "");
        if (receipt.length() > 40) {
            receipt = receipt.substring(0, 40);
        }
        payment.gatewayOrderId = razorpay.createOrder(quote.amountPaise(), quote.currency(), receipt);
        payments.save(payment);
        audit.record(event.userId, "PAYMENT_CREATED", "PAYMENT", payment.id, Map.of("gateway", "RAZORPAY"), ip);
        Map<String, Object> body = view(payment);
        body.put("keyId", properties.getRazorpay().getKeyId());
        body.put("bypass", false);
        return body;
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature, String eventId) {
        if (!RazorpaySignatures.valid(properties.getRazorpay().getWebhookSecret(), rawBody, signature)) {
            throw ApiException.badRequest("The payment notification could not be verified.");
        }
        String id = (eventId == null || eventId.isBlank())
                ? com.prism.shared.util.Hashes.sha256(rawBody)
                : eventId.trim();
        if (webhooks.existsByGatewayAndEventId("RAZORPAY", id)) {
            return;
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            throw ApiException.badRequest("The payment notification could not be read.");
        }
        PaymentWebhookEvent stored = new PaymentWebhookEvent();
        stored.gateway = "RAZORPAY";
        stored.eventId = id;
        stored.payload = payload;
        stored.processedAt = Instant.now();
        try {
            webhooks.saveAndFlush(stored);
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }
        String type = payload.path("event").asText("");
        if (!"payment.captured".equals(type)) {
            return;
        }
        JsonNode entity = payload.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asText("");
        String gatewayPaymentId = entity.path("id").asText("");
        if (orderId.isBlank()) {
            return;
        }
        payments.findByGatewayAndGatewayOrderId("RAZORPAY", orderId).ifPresent(payment -> {
            if (!"PAID".equals(payment.status)) {
                completion.complete(payment.id, gatewayPaymentId, "RAZORPAY", null);
            }
        });
    }

    @Transactional
    public int expirePending(Instant cutoff) {
        int count = 0;
        for (Payment payment : payments.findByStatusAndCreatedAtBefore("PENDING", cutoff)) {
            payment.status = "EXPIRED";
            payments.save(payment);
            count++;
        }
        return count;
    }

    public Map<String, Object> view(Payment payment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", payment.id);
        body.put("eventId", payment.eventId);
        body.put("paymentType", payment.paymentType);
        body.put("gateway", payment.gateway);
        body.put("gatewayOrderId", payment.gatewayOrderId);
        body.put("gatewayPaymentId", payment.gatewayPaymentId);
        body.put("amountPaise", payment.amountPaise);
        body.put("days", payment.days);
        body.put("dailyRatePaise", payment.dailyRatePaise);
        body.put("currency", payment.currency);
        body.put("status", payment.status);
        body.put("verifiedAt", payment.verifiedAt);
        body.put("createdAt", payment.createdAt);
        return body;
    }

    private void ensurePayable(EventSite event, String type) {
        if ("REJECTED".equals(event.status) || "DRAFT".equals(event.status) || "SUBMITTED".equals(event.status)
                || "IN_REVIEW".equals(event.status) || "CHANGES_REQUESTED".equals(event.status)) {
            throw ApiException.badRequest("Payment opens after the site is approved.");
        }
        if (event.approvedRevisionId == null && event.publishedRevisionId == null) {
            throw ApiException.badRequest("Payment opens after the site is approved.");
        }
        if (event.approvedRevisionId != null) {
            EventRevision revision = revisions.findById(event.approvedRevisionId).orElse(null);
            if (revision != null && !"APPROVED".equals(revision.submissionStatus) && event.publishedRevisionId == null) {
                throw ApiException.badRequest("Payment opens after the site is approved.");
            }
        }
        boolean covered = hosting.covers(event.id, Instant.now());
        if ("INITIAL_HOSTING".equals(type) && covered) {
            throw ApiException.conflict("This site already has hosting. Extend it instead.");
        }
        if ("HOSTING_EXTENSION".equals(type) && event.publishedRevisionId == null && event.approvedRevisionId == null) {
            throw ApiException.badRequest("Publish the site before extending hosting.");
        }
    }

    private String normalizeType(EventSite event, String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            return hosting.covers(event.id, Instant.now()) || "EXPIRED".equals(event.status)
                    ? "HOSTING_EXTENSION"
                    : "INITIAL_HOSTING";
        }
        if (!"INITIAL_HOSTING".equals(paymentType) && !"HOSTING_EXTENSION".equals(paymentType)) {
            throw ApiException.badRequest("Choose an initial hosting payment or an extension.");
        }
        return paymentType;
    }
}

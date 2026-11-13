package com.prism.payment;

import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.hosting.HostingPeriod;
import com.prism.hosting.HostingService;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.security.AuthContext;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService payments;
    private final PaymentRepository repository;
    private final EventService events;
    private final EventRepository eventsRepository;
    private final HostingService hosting;

    public PaymentController(PaymentService payments, PaymentRepository repository, EventService events, EventRepository eventsRepository, HostingService hosting) {
        this.payments = payments;
        this.repository = repository;
        this.events = events;
        this.eventsRepository = eventsRepository;
        this.hosting = hosting;
    }

    @GetMapping("/payments")
    public ApiResponse<List<Map<String, Object>>> mine() {
        UserPrincipal principal = AuthContext.require();
        List<EventSite> owned = eventsRepository.findByUserId(principal.id(), PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
        if (owned.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (EventSite event : owned) {
            names.put(event.id, event.eventName);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment payment : repository.findByEventIdInOrderByCreatedAtDesc(names.keySet())) {
            Map<String, Object> body = new LinkedHashMap<>(payments.view(payment));
            body.put("eventName", names.getOrDefault(payment.eventId, ""));
            rows.add(body);
        }
        return ApiResponse.ok(rows);
    }

    @PostMapping("/events/{id}/payments")
    public ApiResponse<Map<String, Object>> pay(@PathVariable UUID id, @RequestBody PaymentRequest request, HttpServletRequest http) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        return ApiResponse.ok(payments.start(event, request.paymentType(), request.days(), request.method(), Requests.clientIp(http)));
    }

    @PostMapping("/events/{id}/extend")
    public ApiResponse<Map<String, Object>> extend(@PathVariable UUID id, @RequestBody PaymentRequest request, HttpServletRequest http) {
        EventSite event = events.requireOwner(id, AuthContext.require());
        return ApiResponse.ok(payments.start(event, "HOSTING_EXTENSION", request.days(), request.method(), Requests.clientIp(http)));
    }

    @GetMapping("/payments/{id}")
    public ApiResponse<Map<String, Object>> one(@PathVariable UUID id) {
        var principal = AuthContext.require();
        Payment payment = repository.findById(id).orElseThrow(() -> com.prism.shared.exception.ApiException.notFound("That payment was not found."));
        events.requireReadable(payment.eventId, principal);
        return ApiResponse.ok(payments.view(payment));
    }

    @GetMapping("/events/{id}/hosting")
    public ApiResponse<List<HostingPeriod>> hosting(@PathVariable UUID id) {
        EventSite event = events.requireReadable(id, AuthContext.require());
        return ApiResponse.ok(hosting.forEvent(event.id));
    }

    @PostMapping("/payments/webhooks/razorpay")
    public ResponseEntity<Void> webhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId
    ) {
        payments.handleWebhook(body, signature, eventId);
        return ResponseEntity.ok().build();
    }

    public record PaymentRequest(String paymentType, Integer days, String method) {
    }
}

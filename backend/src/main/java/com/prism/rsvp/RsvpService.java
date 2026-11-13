package com.prism.rsvp;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.event.EventSite;
import com.prism.notification.OutboxService;
import com.prism.revision.EventRevision;
import com.prism.shared.exception.ApiException;
import com.prism.shared.util.PhoneNumbers;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RsvpService {

    private final RsvpRepository rsvps;
    private final OutboxService outbox;
    private final UserRepository users;

    public RsvpService(RsvpRepository rsvps, OutboxService outbox, UserRepository users) {
        this.rsvps = rsvps;
        this.outbox = outbox;
        this.users = users;
    }

    @Transactional
    public Map<String, Object> submit(EventSite event, EventRevision published, RsvpCommand command, String ip) {
        JsonNode rsvp = published.configurationJson.path("rsvp");
        if (!rsvp.path("enabled").asBoolean(false)) {
            throw ApiException.badRequest("This site is not taking replies.");
        }
        String deadline = rsvp.path("deadline").asText("");
        if (!deadline.isBlank() && !Instant.now().isBefore(Instant.parse(deadline))) {
            throw ApiException.badRequest("Replies for this site have closed.");
        }
        if (ip != null && rsvps.countByEventIdAndRequestIpAndSubmittedAtAfter(event.id, ip, Instant.now().minusSeconds(3600)) >= 8) {
            throw ApiException.tooMany("Too many replies from this network. Try again later.");
        }
        if (command.website() != null && !command.website().isBlank()) {
            return Map.of("accepted", true);
        }
        String name = required(command.guestName(), "Enter your name.");
        if (name.length() > 160) {
            throw ApiException.badRequest("That name is too long.");
        }
        String attendance = command.attendanceStatus() == null ? "" : command.attendanceStatus().trim().toUpperCase();
        if (!"ACCEPTED".equals(attendance) && !"DECLINED".equals(attendance)) {
            throw ApiException.badRequest("Say whether you can attend.");
        }
        int max = Math.max(1, rsvp.path("maxGuestsPerResponse").asInt(4));
        int guests = "DECLINED".equals(attendance) ? 0 : Math.max(1, command.numberOfGuests() == null ? 1 : command.numberOfGuests());
        if (!rsvp.path("allowPlusOne").asBoolean(true)) {
            guests = Math.min(guests, 1);
        }
        if (guests > max) {
            throw ApiException.badRequest("This reply can include up to " + max + " guests.");
        }
        String phone = null;
        if (rsvp.path("collectPhone").asBoolean(false)) {
            if (command.guestPhone() == null || command.guestPhone().isBlank()) {
                throw ApiException.badRequest("Enter a phone number.");
            }
            phone = PhoneNumbers.normalize(command.guestPhone());
        } else if (command.guestPhone() != null && !command.guestPhone().isBlank()) {
            phone = PhoneNumbers.normalize(command.guestPhone());
        }
        String email = blank(command.guestEmail());
        if (rsvp.path("collectEmail").asBoolean(false) && (email == null || !email.contains("@"))) {
            throw ApiException.badRequest("Enter an email address.");
        }
        if (email != null && (email.length() > 254 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw ApiException.badRequest("Enter a valid email address.");
        }
        String message = blank(command.message());
        if (message != null && (message.indexOf('<') >= 0 || message.indexOf('>') >= 0)) {
            throw ApiException.badRequest("The note cannot include markup.");
        }
        if (message != null && message.length() > 500) {
            message = message.substring(0, 500);
        }
        EventRsvp row = new EventRsvp();
        row.eventId = event.id;
        row.guestName = name;
        row.guestPhone = phone;
        row.guestEmail = email;
        row.attendanceStatus = attendance;
        row.numberOfGuests = guests;
        row.message = message;
        row.requestIp = ip;
        rsvps.save(row);
        UserAccount owner = users.findById(event.userId).orElse(null);
        if (owner != null) {
            outbox.enqueue("RSVP", row.id, "RSVP_NOTIFICATION", Map.of(
                    "phone", owner.phoneNumber,
                    "name", owner.fullName,
                    "eventName", event.eventName,
                    "guestName", name,
                    "attendance", attendance
            ), null);
        }
        String confirmation = rsvp.path("confirmationMessage").asText("");
        if (confirmation.isBlank()) {
            confirmation = "Your reply has been recorded.";
        }
        return Map.of("id", row.id, "confirmationMessage", confirmation);
    }

    public List<Map<String, Object>> list(UUID eventId) {
        return rsvps.findByEventIdOrderBySubmittedAtDesc(eventId).stream().map(this::view).toList();
    }

    public Map<String, Object> summary(UUID eventId) {
        long total = rsvps.countByEventId(eventId);
        long accepted = rsvps.countByEventIdAndAttendanceStatus(eventId, "ACCEPTED");
        long declined = rsvps.countByEventIdAndAttendanceStatus(eventId, "DECLINED");
        long guests = rsvps.sumAcceptedGuests(eventId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("accepted", accepted);
        body.put("declined", declined);
        body.put("expectedGuests", guests);
        return body;
    }

    @Transactional
    public void delete(EventSite event, UUID rsvpId) {
        EventRsvp row = rsvps.findByIdAndEventId(rsvpId, event.id)
                .orElseThrow(() -> ApiException.notFound("That reply was not found."));
        rsvps.delete(row);
    }

    public byte[] csv(UUID eventId) {
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("guest_name,guest_phone,guest_email,attendance,guests,message,submitted_at\n");
        for (EventRsvp row : rsvps.findByEventIdOrderBySubmittedAtDesc(eventId)) {
            builder.append(cell(row.guestName)).append(',')
                    .append(cell(row.guestPhone)).append(',')
                    .append(cell(row.guestEmail)).append(',')
                    .append(cell(row.attendanceStatus)).append(',')
                    .append(row.numberOfGuests).append(',')
                    .append(cell(row.message)).append(',')
                    .append(row.submittedAt).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> view(EventRsvp row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.id);
        body.put("guestName", row.guestName);
        body.put("guestPhone", row.guestPhone);
        body.put("guestEmail", row.guestEmail);
        body.put("attendanceStatus", row.attendanceStatus);
        body.put("numberOfGuests", row.numberOfGuests);
        body.put("message", row.message);
        body.put("submittedAt", row.submittedAt);
        return body;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public record RsvpCommand(
            String guestName,
            String guestPhone,
            String guestEmail,
            String attendanceStatus,
            Integer numberOfGuests,
            String message,
            String website
    ) {
    }
}

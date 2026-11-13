package com.prism;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import com.prism.auth.OtpRepository;
import com.prism.event.EventRepository;
import com.prism.hosting.HostingPeriodRepository;
import com.prism.hosting.HostingService;
import com.prism.payment.PaymentWebhookRepository;
import com.prism.publishing.PublishingService;
import com.prism.shared.audit.AuditLogRepository;
import com.prism.shared.util.Hashes;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PrismJourneyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired OtpRepository otps;
    @Autowired AuditLogRepository audits;
    @Autowired EventRepository events;
    @Autowired HostingPeriodRepository hostingPeriods;
    @Autowired HostingService hosting;
    @Autowired PublishingService publishing;
    @Autowired PaymentWebhookRepository webhooks;

    @Test
    void customerBuildsASiteAndItExpiresAfterHostingEnds() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9876543210\"}"))
                .andExpect(status().isOk());
        otps.findFirstByPhoneNumberAndVerifiedAtIsNullOrderByCreatedAtDesc("919876543210").ifPresent(row -> {
            row.createdAt = Instant.now().minusSeconds(120);
            row.expiresAt = Instant.now().minusSeconds(30);
            otps.saveAndFlush(row);
        });
        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9876543210\",\"otp\":\"111111\",\"fullName\":\"Meera Iyer\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9876543210\"}"))
                .andExpect(status().isOk());
        Session customer = session(mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9876543210\",\"otp\":\"111111\",\"fullName\":\"Meera Iyer\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(audits.findAll().stream().anyMatch(row -> "OTP_BYPASS".equals(row.action)));

        MvcResult categories = customer.perform(get("/api/v1/categories")).andExpect(status().isOk()).andReturn();
        String categoryId = first(JsonPath.read(categories.getResponse().getContentAsString(), "$.data[?(@.code=='WEDDING')].id"));
        MvcResult templates = customer.perform(get("/api/v1/categories/" + categoryId + "/templates")).andExpect(status().isOk()).andReturn();
        String templateId = first(JsonPath.read(templates.getResponse().getContentAsString(), "$.data[?(@.code=='WEDDING_LINEN')].id"));

        MvcResult created = customer.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","templateId":"%s","eventName":"Meera and Arun","slug":"meera-and-arun","requestedDays":7}
                                """.formatted(categoryId, templateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String eventId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        Session stranger = register("912222222222", "Kabir Shah");
        stranger.perform(get("/api/v1/events/" + eventId)).andExpect(status().isNotFound());
        stranger.perform(post("/api/v1/admin/events/" + eventId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"\"}"))
                .andExpect(status().isForbidden());

        customer.perform(post("/api/v1/events/" + eventId + "/submit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        Session admin = session(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9000000001\",\"password\":\"PrismAdmin1\"}"))
                .andExpect(status().isOk())
                .andReturn());
        admin.perform(post("/api/v1/admin/events/" + eventId + "/request-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Say where the courtyard gate is.\"}"))
                .andExpect(status().isOk());
        customer.perform(get("/api/v1/events/" + eventId))
                .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.latestReview.comments").value("Say where the courtyard gate is."));

        Object configuration = JsonPath.read(customer.perform(get("/api/v1/events/" + eventId))
                .andReturn().getResponse().getContentAsString(), "$.data.configuration");
        customer.perform(put("/api/v1/events/" + eventId + "/configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(configuration)))
                .andExpect(status().isOk());
        customer.perform(post("/api/v1/events/" + eventId + "/submit")).andExpect(status().isOk());

        admin.perform(post("/api/v1/admin/events/" + eventId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"\"}"))
                .andExpect(status().isOk());
        customer.perform(get("/api/v1/events/" + eventId + "/quote"))
                .andExpect(jsonPath("$.data.amountPaise").value(350000))
                .andExpect(jsonPath("$.data.formatted").value("₹3,500"));

        customer.perform(post("/api/v1/events/" + eventId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"INITIAL_HOSTING\",\"days\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.gateway").value("BYPASS"))
                .andExpect(jsonPath("$.data.gatewayOrderId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.gatewayPaymentId").value(org.hamcrest.Matchers.nullValue()));
        assertTrue(audits.findAll().stream().anyMatch(row -> "PAYMENT_BYPASS".equals(row.action)));

        mvc.perform(get("/api/v1/public/events/meera-and-arun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.configuration.event.name").value("Meera and Arun"));
        mvc.perform(get("/render/meera-and-arun"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("<title>Meera and Arun</title>")));

        mvc.perform(post("/api/v1/public/events/meera-and-arun/rsvp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guestName":"Asha Nair","guestPhone":"9876500001","attendanceStatus":"ACCEPTED","numberOfGuests":2,"message":"We will be there"}
                                """))
                .andExpect(status().isOk());
        customer.perform(get("/api/v1/events/" + eventId + "/rsvps/summary"))
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.expectedGuests").value(2));
        stranger.perform(get("/api/v1/events/" + eventId + "/rsvps")).andExpect(status().isNotFound());

        customer.perform(post("/api/v1/events/" + eventId + "/extend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPaise").value(150000));

        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test\",\"order_id\":\"order_missing\"}}}}";
        String signature = Hashes.hmacSha256("test-webhook-secret", body);
        mvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", "evt_duplicate")
                        .content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", "evt_duplicate")
                        .content(body))
                .andExpect(status().isOk());
        assertEquals(1, webhooks.findAll().size());

        hostingPeriods.findByEventIdOrderByStartAtAsc(UUID.fromString(eventId)).forEach(period -> {
            period.endAt = Instant.now().minusSeconds(5);
            period.startAt = period.endAt.minusSeconds(3600);
            period.status = "ACTIVE";
            hostingPeriods.save(period);
        });
        hosting.expireDue(Instant.now()).forEach(id -> events.findById(id).ifPresent(event -> publishing.markExpired(event, Instant.now())));
        mvc.perform(get("/api/v1/public/events/meera-and-arun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("EXPIRED"))
                .andExpect(jsonPath("$.data.configuration").doesNotExist());

        admin.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nila Reviewer\",\"phoneNumber\":\"9000000002\",\"password\":\"ReviewerPass1\",\"role\":\"REVIEWER\"}"))
                .andExpect(status().isOk());
        Session reviewer = session(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9000000002\",\"password\":\"ReviewerPass1\"}"))
                .andExpect(status().isOk())
                .andReturn());
        reviewer.perform(delete("/api/v1/events/" + eventId)).andExpect(status().isForbidden());
        stranger.perform(delete("/api/v1/events/" + eventId)).andExpect(status().isNotFound());

        customer.perform(delete("/api/v1/events/" + eventId)).andExpect(status().isOk());
        customer.perform(get("/api/v1/events/" + eventId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/events/meera-and-arun")).andExpect(status().isNotFound());
        assertTrue(events.findBySlug("meera-and-arun").isEmpty());
        assertTrue(audits.findAll().stream().anyMatch(row -> "EVENT_DELETED".equals(row.action)));

        MvcResult replacement = customer.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","templateId":"%s","eventName":"Meera and Arun","slug":"meera-and-arun","requestedDays":7}
                                """.formatted(categoryId, templateId)))
                .andExpect(status().isOk())
                .andReturn();
        String replacementId = JsonPath.read(replacement.getResponse().getContentAsString(), "$.data.id");
        admin.perform(delete("/api/v1/events/" + replacementId)).andExpect(status().isOk());
        customer.perform(get("/api/v1/events/" + replacementId)).andExpect(status().isNotFound());
    }

    private static String first(JSONArray values) {
        return String.valueOf(values.getFirst());
    }

    private Session register(String phone, String name) throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        return session(mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"otp\":\"111111\",\"fullName\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Session session(MvcResult result) {
        Cookie access = result.getResponse().getCookie("prism_access");
        Cookie csrf = result.getResponse().getCookie("prism_csrf");
        return new Session(access, csrf);
    }

    private final class Session {
        private final Cookie access;
        private final Cookie csrf;

        private Session(Cookie access, Cookie csrf) {
            this.access = access;
            this.csrf = csrf;
        }

        private org.springframework.test.web.servlet.ResultActions perform(org.springframework.test.web.servlet.RequestBuilder builder) throws Exception {
            if (builder instanceof org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
                return mvc.perform(request.cookie(access, csrf).header("X-CSRF-Token", csrf.getValue()));
            }
            return mvc.perform(builder);
        }
    }
}

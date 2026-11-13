package com.prism.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.prism.category.CategoryRepository;
import com.prism.category.EventCategory;
import com.prism.event.EventRepository;
import com.prism.event.EventService;
import com.prism.event.EventSite;
import com.prism.hosting.HostingPeriod;
import com.prism.hosting.HostingService;
import com.prism.payment.PaymentRepository;
import com.prism.publishing.PublishingService;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.audit.AuditLogRepository;
import com.prism.shared.audit.AuditService;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.PhoneNumbers;
import com.prism.template.EventTemplate;
import com.prism.template.TemplateRepository;
import com.prism.template.TemplateVersion;
import com.prism.template.TemplateVersionRepository;
import com.prism.user.AccountService;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import com.prism.user.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository users;
    private final UserRoleRepository roles;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final EventRepository events;
    private final EventService eventService;
    private final RevisionRepository revisions;
    private final PaymentRepository payments;
    private final AuditLogRepository audits;
    private final AuditService audit;
    private final CategoryRepository categories;
    private final TemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final HostingService hosting;
    private final PublishingService publishing;

    public AdminService(
            UserRepository users,
            UserRoleRepository roles,
            AccountService accounts,
            PasswordEncoder passwordEncoder,
            EventRepository events,
            EventService eventService,
            RevisionRepository revisions,
            PaymentRepository payments,
            AuditLogRepository audits,
            AuditService audit,
            CategoryRepository categories,
            TemplateRepository templates,
            TemplateVersionRepository versions,
            HostingService hosting,
            PublishingService publishing
    ) {
        this.users = users;
        this.roles = roles;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.eventService = eventService;
        this.revisions = revisions;
        this.payments = payments;
        this.audits = audits;
        this.audit = audit;
        this.categories = categories;
        this.templates = templates;
        this.versions = versions;
        this.hosting = hosting;
        this.publishing = publishing;
    }

    public Map<String, Object> dashboard() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startToday = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startMonth = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long waiting = revisions.countBySubmissionStatusIn(List.of("SUBMITTED", "IN_REVIEW"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("waitingOnReview", waiting);
        body.put("published", events.countByStatus("PUBLISHED"));
        body.put("customers", users.count());
        body.put("paidTodayPaise", payments.sumPaidBetween(startToday, startToday.plus(1, ChronoUnit.DAYS)));
        body.put("paidThisMonthPaise", payments.sumPaidBetween(startMonth, startToday.plus(1, ChronoUnit.DAYS)));
        List<Map<String, Object>> ending = new ArrayList<>();
        for (HostingPeriod period : hosting.endingBetween(now, now.plus(7, ChronoUnit.DAYS))) {
            events.findById(period.eventId).ifPresent(event -> ending.add(Map.of(
                    "eventId", event.id,
                    "eventName", event.eventName,
                    "slug", event.slug,
                    "endAt", period.endAt
            )));
        }
        body.put("hostingEndingSoon", ending);
        body.put("submissions", submissions(now.minus(13, ChronoUnit.DAYS)));
        return body;
    }

    public Page<AccountService.AccountView> users(String query, Pageable pageable) {
        Page<UserAccount> page = (query == null || query.isBlank())
                ? users.findAll(pageable)
                : users.search(query.trim(), pageable);
        return page.map(accounts::view);
    }

    @Transactional
    public AccountService.AccountView createStaff(String fullName, String phone, String password, String role, UserPrincipal actor, String ip) {
        if (!"ADMIN".equals(role) && !"REVIEWER".equals(role)) {
            throw ApiException.badRequest("Choose an admin or a reviewer.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw ApiException.badRequest("Enter a name.");
        }
        if (password == null || password.length() < 10) {
            throw ApiException.badRequest("Use a password of at least 10 characters.");
        }
        String normalized = PhoneNumbers.normalize(phone);
        if (users.existsByPhoneNumber(normalized)) {
            throw ApiException.conflict("That mobile number already has an account.");
        }
        UserAccount user = new UserAccount();
        user.fullName = fullName.trim();
        user.phoneNumber = normalized;
        user.passwordHash = passwordEncoder.encode(password);
        user.status = "ACTIVE";
        users.save(user);
        accounts.grant(user.id, role);
        audit.record(actor.id(), "USER_CREATED", "USER", user.id, Map.of("role", role), ip);
        return accounts.view(user);
    }

    @Transactional
    public AccountService.AccountView setStatus(UUID userId, String status, UserPrincipal actor, String ip) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw ApiException.badRequest("Choose active or disabled.");
        }
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("That account was not found."));
        if (user.id.equals(actor.id()) && "DISABLED".equals(status)) {
            throw ApiException.badRequest("You cannot disable your own account.");
        }
        user.status = status;
        users.save(user);
        audit.record(actor.id(), "USER_UPDATED", "USER", user.id, Map.of("status", status), ip);
        return accounts.view(user);
    }

    @Transactional
    public EventCategory saveCategory(UUID id, String name, String code, String description, boolean active) {
        EventCategory category = id == null ? new EventCategory() : categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("That category was not found."));
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Enter a category name.");
        }
        category.name = name.trim();
        if (category.code == null) {
            category.code = code == null ? name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_") : code.trim().toUpperCase();
        }
        category.description = description == null ? "" : description.trim();
        category.active = active;
        return categories.save(category);
    }

    @Transactional
    public EventTemplate saveTemplate(UUID id, UUID categoryId, String name, String description, boolean active) {
        EventTemplate template = id == null ? new EventTemplate() : templates.findById(id)
                .orElseThrow(() -> ApiException.notFound("That template was not found."));
        if (template.id == null) {
            EventCategory category = categories.findById(categoryId)
                    .orElseThrow(() -> ApiException.badRequest("Choose a category."));
            template.categoryId = category.id;
            template.code = name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        }
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Enter a template name.");
        }
        template.name = name.trim();
        template.description = description == null ? "" : description.trim();
        template.active = active;
        return templates.save(template);
    }

    @Transactional
    public TemplateVersion addVersion(UUID templateId, JsonNode schema, JsonNode defaults, boolean active) {
        EventTemplate template = templates.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("That template was not found."));
        int next = versions.findByTemplateIdOrderByVersionNumberDesc(template.id).stream()
                .mapToInt(version -> version.versionNumber).max().orElse(0) + 1;
        if (active) {
            versions.findByTemplateIdOrderByVersionNumberDesc(template.id).forEach(version -> version.active = false);
        }
        TemplateVersion version = new TemplateVersion();
        version.templateId = template.id;
        version.versionNumber = next;
        version.schemaJson = schema;
        version.defaultConfiguration = defaults;
        version.active = active;
        return versions.save(version);
    }

    @Transactional
    public Map<String, Object> suspend(UUID eventId, UserPrincipal actor, String ip) {
        EventSite event = events.lockById(eventId).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        if (!"PUBLISHED".equals(event.status)) {
            throw ApiException.badRequest("Only a live site can be suspended.");
        }
        event.status = "SUSPENDED";
        events.save(event);
        audit.record(actor.id(), "EVENT_SUSPENDED", "EVENT", event.id, Map.of(), ip);
        return eventService.present(event, eventService.latest(event.id));
    }

    @Transactional
    public Map<String, Object> unpublish(UUID eventId, UserPrincipal actor, String ip) {
        EventSite event = events.lockById(eventId).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        publishing.unpublish(event, Instant.now());
        audit.record(actor.id(), "EVENT_UNPUBLISHED", "EVENT", event.id, Map.of(), ip);
        return eventService.present(event, eventService.latest(event.id));
    }

    @Transactional
    public Map<String, Object> restore(UUID eventId, UserPrincipal actor, String ip) {
        EventSite event = events.lockById(eventId).orElseThrow(() -> ApiException.notFound("That event site was not found."));
        if (event.publishedRevisionId == null && event.approvedRevisionId == null) {
            throw ApiException.badRequest("This site has nothing to restore.");
        }
        if (!hosting.covers(event.id, Instant.now())) {
            throw ApiException.badRequest("Hosting has ended. The customer needs to extend it before the site can return.");
        }
        UUID revisionId = event.publishedRevisionId != null ? event.publishedRevisionId : event.approvedRevisionId;
        publishing.publish(event, revisionId, Instant.now());
        audit.record(actor.id(), "EVENT_RESTORED", "EVENT", event.id, Map.of(), ip);
        return eventService.present(event, eventService.latest(event.id));
    }

    public Page<com.prism.shared.audit.AuditLog> audit(Pageable pageable) {
        return audits.findAllByOrderByCreatedAtDesc(pageable);
    }

    private List<Map<String, Object>> submissions(Instant from) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        LocalDate start = LocalDate.ofInstant(from, ZoneOffset.UTC);
        for (int i = 0; i < 14; i++) {
            counts.put(start.plusDays(i), 0L);
        }
        for (EventRevision revision : revisions.findBySubmittedAtAfter(from)) {
            if (revision.submittedAt == null) {
                continue;
            }
            LocalDate day = LocalDate.ofInstant(revision.submittedAt, ZoneOffset.UTC);
            counts.computeIfPresent(day, (key, value) -> value + 1);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((day, count) -> rows.add(Map.of("day", day.toString(), "count", count)));
        return rows;
    }
}

package com.prism.auth;

import com.prism.notification.OutboxService;
import com.prism.notification.whatsapp.WhatsAppService;
import com.prism.shared.audit.AuditService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.CookieService;
import com.prism.shared.security.JwtAuthenticationFilter;
import com.prism.shared.security.JwtService;
import com.prism.shared.security.UserPrincipal;
import com.prism.shared.util.Hashes;
import com.prism.shared.util.PhoneNumbers;
import com.prism.user.AccountService;
import com.prism.user.UserAccount;
import com.prism.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final OtpRepository otps;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AccountService accounts;
    private final PrismProperties properties;
    private final WhatsAppService whatsApp;
    private final JwtService jwt;
    private final CookieService cookies;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle loginThrottle;
    private final OutboxService outbox;

    public AuthService(
            OtpRepository otps,
            RefreshTokenRepository refreshTokens,
            UserRepository users,
            AccountService accounts,
            PrismProperties properties,
            WhatsAppService whatsApp,
            JwtService jwt,
            CookieService cookies,
            AuditService audit,
            PasswordEncoder passwordEncoder,
            LoginThrottle loginThrottle,
            OutboxService outbox
    ) {
        this.otps = otps;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.accounts = accounts;
        this.properties = properties;
        this.whatsApp = whatsApp;
        this.jwt = jwt;
        this.cookies = cookies;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.loginThrottle = loginThrottle;
        this.outbox = outbox;
    }

    @Transactional
    public OtpRequestView requestOtp(String rawPhone, String ip) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Instant now = Instant.now();
        if (otps.countByPhoneNumberAndCreatedAtAfter(phone, now.minusSeconds(3600)) >= OtpCodes.HOURLY_PHONE_LIMIT) {
            throw ApiException.tooMany("Too many codes were requested for this number. Try again later.");
        }
        if (ip != null && otps.countByRequestIpAndCreatedAtAfter(ip, now.minusSeconds(3600)) >= OtpCodes.HOURLY_IP_LIMIT) {
            throw ApiException.tooMany("Too many codes were requested from this network. Try again later.");
        }
        otps.findFirstByPhoneNumberAndVerifiedAtIsNullOrderByCreatedAtDesc(phone).ifPresent(open -> {
            if (open.createdAt.isAfter(now.minusSeconds(OtpCodes.RESEND_SECONDS)) && open.expiresAt.isAfter(now)) {
                throw ApiException.tooMany("Wait a minute before requesting another code.");
            }
        });
        otps.expireOpen(phone, now);
        String code = OtpCodes.generate();
        if (whatsApp.enabled()) {
            whatsApp.sendTemplate(phone, properties.getWhatsapp().getOtpTemplateName(), List.of(code));
        } else if (!properties.isOtpBypassEnabled()) {
            throw ApiException.unavailable("WhatsApp is not configured, so a code cannot be sent.");
        }
        OtpVerification verification = new OtpVerification();
        verification.phoneNumber = phone;
        verification.otpHash = OtpCodes.hash(properties.getJwt().getSecret(), phone, code);
        verification.expiresAt = now.plusSeconds(OtpCodes.TTL_SECONDS);
        verification.attempts = 0;
        verification.requestIp = ip;
        otps.save(verification);
        audit.record(null, "OTP_REQUESTED", "OTP", verification.id, Map.of("phone", phone), ip);
        return new OtpRequestView(OtpCodes.TTL_SECONDS, OtpCodes.RESEND_SECONDS);
    }

    @Transactional
    public AccountService.AccountView verifyOtp(String rawPhone, String code, String fullName, String ip, HttpServletResponse response) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (code == null || !code.matches("\\d{6}")) {
            throw ApiException.badRequest("Enter the six-digit code.");
        }
        Instant now = Instant.now();
        OtpVerification verification = otps.findFirstByPhoneNumberAndVerifiedAtIsNullOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> ApiException.badRequest("Request a code first."));
        if (OtpCodes.expired(verification.expiresAt, now)) {
            throw ApiException.badRequest("That code has expired. Request a new one.");
        }
        if (OtpCodes.attemptsExhausted(verification.attempts)) {
            throw ApiException.tooMany("Too many attempts. Request a new code.");
        }
        boolean bypass = OtpCodes.bypassAccepted(properties.isOtpBypassEnabled(), code);
        boolean matches = OtpCodes.matches(properties.getJwt().getSecret(), phone, code, verification.otpHash);
        if (!bypass && !matches) {
            verification.attempts += 1;
            otps.save(verification);
            throw ApiException.badRequest("That code is not valid.");
        }
        UserAccount existing = users.findByPhoneNumber(phone).orElse(null);
        if (existing == null && (fullName == null || fullName.isBlank() || fullName.trim().length() < 2)) {
            throw ApiException.badRequest("Enter the name for this account.");
        }
        if (existing != null && !"ACTIVE".equals(existing.status)) {
            throw ApiException.forbidden("This account is disabled.");
        }
        verification.verifiedAt = now;
        otps.save(verification);
        UserAccount user = existing;
        boolean created = false;
        if (user == null) {
            user = accounts.createCustomer(fullName, phone);
            created = true;
        }
        establish(user, response);
        audit.record(user.id, bypass ? "OTP_BYPASS" : "OTP_VERIFIED", "USER", user.id,
                Map.of("method", bypass ? "OTP_BYPASS" : "OTP"), ip);
        if (created) {
            outbox.enqueue("USER", user.id, "REGISTRATION_CONFIRMATION", Map.of(
                    "phone", user.phoneNumber,
                    "name", user.fullName
            ), "registration:" + user.id);
        }
        return accounts.view(user);
    }

    @Transactional
    public AccountService.AccountView login(String rawPhone, String password, String ip, HttpServletResponse response) {
        String phone = PhoneNumbers.normalize(rawPhone);
        loginThrottle.check(phone);
        UserAccount user = users.findByPhoneNumber(phone).orElse(null);
        UserPrincipal principal = user == null ? null : accounts.principalOf(user);
        boolean staff = principal != null && principal.isStaff();
        if (user == null || user.passwordHash == null || !staff || !passwordEncoder.matches(password, user.passwordHash)) {
            loginThrottle.fail(phone);
            audit.record(user == null ? null : user.id, "LOGIN_FAILED", "USER", user == null ? null : user.id, Map.of(), ip);
            throw ApiException.unauthorized("Those sign-in details are not valid.");
        }
        if (!"ACTIVE".equals(user.status)) {
            throw ApiException.forbidden("This account is disabled.");
        }
        loginThrottle.clear(phone);
        establish(user, response);
        audit.record(user.id, "LOGIN", "USER", user.id, Map.of("method", "PASSWORD"), ip);
        return accounts.view(user);
    }

    @Transactional
    public AccountService.AccountView refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = JwtAuthenticationFilter.cookie(request, CookieService.REFRESH);
        if (raw == null) {
            throw ApiException.unauthorized("Sign in to continue.");
        }
        RefreshToken token = refreshTokens.findByTokenHash(Hashes.sha256(raw)).orElse(null);
        Instant now = Instant.now();
        if (token == null || token.revokedAt != null || !token.expiresAt.isAfter(now)) {
            cookies.clear(response);
            throw ApiException.unauthorized("Sign in to continue.");
        }
        token.revokedAt = now;
        refreshTokens.save(token);
        UserAccount user = accounts.require(token.userId);
        establish(user, response);
        return accounts.view(user);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response, UUID actorId, String ip) {
        String raw = JwtAuthenticationFilter.cookie(request, CookieService.REFRESH);
        if (raw != null) {
            refreshTokens.findByTokenHash(Hashes.sha256(raw)).ifPresent(token -> {
                token.revokedAt = Instant.now();
                refreshTokens.save(token);
            });
        }
        cookies.clear(response);
        if (actorId != null) {
            audit.record(actorId, "LOGOUT", "USER", actorId, Map.of(), ip);
        }
    }

    public AccountService.AccountView me(UserPrincipal principal) {
        return accounts.view(accounts.require(principal.id()));
    }

    @Transactional
    public AccountService.AccountView updateMe(UserPrincipal principal, String fullName, String email) {
        UserAccount user = accounts.require(principal.id());
        if (fullName != null && !fullName.isBlank()) {
            if (fullName.trim().length() < 2 || fullName.trim().length() > 160) {
                throw ApiException.badRequest("Enter a name between 2 and 160 characters.");
            }
            user.fullName = fullName.trim();
        }
        if (email != null) {
            String trimmed = email.trim();
            if (trimmed.isEmpty()) {
                user.email = null;
            } else {
                if (!trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || trimmed.length() > 254) {
                    throw ApiException.badRequest("Enter a valid email address.");
                }
                users.findByPhoneNumber(user.phoneNumber);
                if (users.existsByEmailIgnoreCase(trimmed) && (user.email == null || !user.email.equalsIgnoreCase(trimmed))) {
                    throw ApiException.conflict("That email is already in use.");
                }
                user.email = trimmed;
            }
        }
        users.save(user);
        return accounts.view(user);
    }

    private void establish(UserAccount user, HttpServletResponse response) {
        UserPrincipal principal = accounts.principalOf(user);
        String refresh = cookies.newRefreshToken();
        RefreshToken row = new RefreshToken();
        row.userId = user.id;
        row.tokenHash = Hashes.sha256(refresh);
        row.expiresAt = Instant.now().plusSeconds(properties.getJwt().getRefreshTokenExpiry());
        refreshTokens.save(row);
        cookies.writeSession(response, jwt.accessToken(principal), refresh, cookies.newCsrfToken());
    }

    public record OtpRequestView(int expiresInSeconds, int resendAfterSeconds) {
    }

    public Map<String, Object> sessionPayload(AccountService.AccountView view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", view);
        return body;
    }
}

package com.prism.shared.security;

import com.prism.shared.config.PrismProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CookieService {

    public static final String ACCESS = "prism_access";
    public static final String REFRESH = "prism_refresh";
    public static final String CSRF = "prism_csrf";

    private final PrismProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CookieService(PrismProperties properties) {
        this.properties = properties;
    }

    public String newCsrfToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void writeSession(HttpServletResponse response, String accessToken, String refreshToken, String csrf) {
        add(response, ACCESS, accessToken, "/", properties.getJwt().getAccessTokenExpiry());
        add(response, REFRESH, refreshToken, "/api/v1/auth", properties.getJwt().getRefreshTokenExpiry());
        ResponseCookie csrfCookie = base(CSRF, csrf, "/", properties.getJwt().getRefreshTokenExpiry())
                .httpOnly(false)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS, "", "/", 0);
        add(response, REFRESH, "", "/api/v1/auth", 0);
        ResponseCookie csrfCookie = base(CSRF, "", "/", 0).httpOnly(false).build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    private void add(HttpServletResponse response, String name, String value, String path, long maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(name, value, path, maxAge).httpOnly(true).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path, long maxAge) {
        return ResponseCookie.from(name, value)
                .path(path)
                .maxAge(maxAge)
                .secure(properties.getSecurity().isCookieSecure())
                .sameSite("Lax");
    }
}

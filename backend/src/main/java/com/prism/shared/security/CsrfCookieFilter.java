package com.prism.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.shared.api.ApiError;
import com.prism.shared.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final ObjectMapper objectMapper;

    public CsrfCookieFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE.contains(request.getMethod()) || exempt(request)) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean cookieAuth = authentication != null && "cookie".equals(authentication.getDetails());
        if (!cookieAuth) {
            chain.doFilter(request, response);
            return;
        }
        String expected = JwtAuthenticationFilter.cookie(request, CookieService.CSRF);
        String actual = request.getHeader("X-CSRF-Token");
        if (expected == null || actual == null || !expected.equals(actual)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(
                    new ApiError("FORBIDDEN", "The page session could not be confirmed. Refresh and try again.")));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean exempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/payments/webhooks/")
                || path.equals("/api/v1/auth/otp/request")
                || path.equals("/api/v1/auth/otp/verify")
                || path.equals("/api/v1/auth/login");
    }
}

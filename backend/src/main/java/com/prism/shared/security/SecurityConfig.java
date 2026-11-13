package com.prism.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prism.shared.api.ApiError;
import com.prism.shared.api.ApiResponse;
import com.prism.shared.config.PrismProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final ObjectMapper objectMapper;
    private final PrismProperties properties;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CsrfCookieFilter csrfCookieFilter,
            ObjectMapper objectMapper,
            PrismProperties properties
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.csrfCookieFilter = csrfCookieFilter;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = new ArrayList<>();
        String publicBase = trimOrigin(properties.getPublicBaseUrl());
        if (!publicBase.isBlank()) {
            origins.add(publicBase);
            String wwwSibling = wwwSibling(publicBase);
            if (!wwwSibling.isBlank() && !origins.contains(wwwSibling)) {
                origins.add(wwwSibling);
            }
        }
        if (!origins.contains("http://localhost:5173")) {
            origins.add("http://localhost:5173");
        }
        if (!origins.contains("http://localhost:8088")) {
            origins.add("http://localhost:8088");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static String trimOrigin(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String wwwSibling(String origin) {
        if (origin.startsWith("https://www.")) {
            return "https://" + origin.substring("https://www.".length());
        }
        if (origin.startsWith("http://www.")) {
            return "http://" + origin.substring("http://www.".length());
        }
        if (origin.startsWith("https://")) {
            return "https://www." + origin.substring("https://".length());
        }
        if (origin.startsWith("http://")) {
            return "http://www." + origin.substring("http://".length());
        }
        return "";
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> write(response, 401, "UNAUTHORIZED", "Sign in to continue."))
                        .accessDeniedHandler((request, response, exception) -> write(response, 403, "FORBIDDEN", "You do not have access to this.")))
                .headers(headers -> headers
                        .contentTypeOptions(options -> {})
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/request", "/api/v1/auth/otp/verify", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/config/features", "/api/v1/fonts").permitAll()
                        .requestMatchers("/api/v1/public/**", "/render/**", "/robots.txt", "/sitemap.xml").permitAll()
                        .requestMatchers("/api/v1/payments/webhooks/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "REVIEWER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private void write(jakarta.servlet.http.HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(new ApiError(code, message)));
    }
}

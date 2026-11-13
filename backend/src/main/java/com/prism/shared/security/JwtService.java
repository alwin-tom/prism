package com.prism.shared.security;

import com.prism.shared.config.PrismProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final PrismProperties properties;
    private final SecretKey key;

    public JwtService(PrismProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String accessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.id().toString())
                .claim("phone", principal.phone())
                .claim("name", principal.fullName())
                .claim("roles", String.join(",", principal.roles()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getJwt().getAccessTokenExpiry())))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String rawRoles = claims.get("roles", String.class);
        List<String> roles = rawRoles == null || rawRoles.isBlank()
                ? List.of()
                : Arrays.stream(rawRoles.split(",")).filter(role -> !role.isBlank()).toList();
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("phone", String.class),
                claims.get("name", String.class),
                roles);
    }
}

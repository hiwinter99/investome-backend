package com.investome.api.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Value("${jwt.keep-login-refresh-token-expiration-ms}")
    private long keepLoginRefreshTokenExpirationMs;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        if (secretKeyString == null || secretKeyString.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable is required.");
        }

        if ("change-this-jwt-secret-to-a-long-random-string".equals(secretKeyString)) {
            throw new IllegalStateException("JWT_SECRET must not use the default placeholder value.");
        }

        if (secretKeyString.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long.");
        }

        this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(Long userId, boolean keepLogin) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (keepLogin ? keepLoginRefreshTokenExpirationMs : refreshTokenExpirationMs));

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs(boolean keepLogin) {
        return keepLogin ? keepLoginRefreshTokenExpirationMs : refreshTokenExpirationMs;
    }

    public Long getUserId(String token) {
        String subject = parseClaims(token).getSubject();

        return Long.parseLong(subject);
    }

    public void validateRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Long userId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);
        String authority = role == null || role.isBlank() ? "ROLE_USER" : "ROLE_" + role;

        return new UsernamePasswordAuthenticationToken(
                userId,
                token,
                Collections.singletonList(new SimpleGrantedAuthority(authority))
        );
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

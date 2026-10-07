package com.paytmmoney.seatreservation.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiry;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-hours}") long expiryHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofHours(expiryHours);
    }

    public String issue(String userId, boolean admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("admin", admin)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        boolean admin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
        return new AuthenticatedUser(claims.getSubject(), admin);
    }
}

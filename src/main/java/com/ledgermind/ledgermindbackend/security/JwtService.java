package com.ledgermind.ledgermindbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final long expiryDays;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiry-days}") long expiryDays) {

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryDays = expiryDays;
    }

    public String issue(UUID userId, String email) {
        long nowMs    = System.currentTimeMillis();
        long expiryMs = nowMs + (expiryDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(new Date(nowMs))
                .expiration(new Date(expiryMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the userId embedded in the token, or null if invalid/expired.
     * Never throws — callers treat null as unauthenticated.
     */
    public UUID extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return null;
        }
    }
}
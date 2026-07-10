package com.ledgermind.ledgermindbackend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-test-secret-1234";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 30);
    }

    @Test
    void issueAndExtractRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issue(userId, "user@example.com");

        assertEquals(userId, jwtService.extractUserId(token));
    }

    @Test
    void extractReturnsNullForGarbage() {
        assertNull(jwtService.extractUserId("not-a-jwt"));
    }

    @Test
    void extractReturnsNullForTokenSignedWithDifferentKey() {
        JwtService other = new JwtService(
                "another-secret-another-secret-another-secret-another-secret-99", 30);
        String token = other.issue(UUID.randomUUID(), "user@example.com");

        assertNull(jwtService.extractUserId(token));
    }

    @Test
    void gmailLinkStateRoundTrip() {
        UUID userId = UUID.randomUUID();
        String state = jwtService.issueGmailLinkState(userId);

        assertEquals(userId, jwtService.extractGmailLinkUserId(state));
    }

    @Test
    void loginTokenIsNotAValidGmailLinkState() {
        String loginToken = jwtService.issue(UUID.randomUUID(), "user@example.com");

        assertNull(jwtService.extractGmailLinkUserId(loginToken));
    }

    @Test
    void gmailLinkStateIsNotAValidLoginToken() {
        // The state token travels through Google's redirect URL (browser
        // history, proxy logs...) - it must never authenticate API calls.
        String state = jwtService.issueGmailLinkState(UUID.randomUUID());

        assertNull(jwtService.extractUserId(state));
    }
}

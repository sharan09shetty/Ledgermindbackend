package com.ledgermind.ledgermindbackend.email.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.ledgermind.ledgermindbackend.email.config.GmailClientFactory;
import com.ledgermind.ledgermindbackend.security.JwtService;
import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
@Slf4j
public class GoogleAuthController {

    private final GoogleAuthorizationCodeFlow loginFlow;
    private final GoogleAuthorizationCodeFlow gmailFlow;
    private final GmailClientFactory gmailClientFactory;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public GoogleAuthController(
            @Qualifier("login") GoogleAuthorizationCodeFlow loginFlow,
            @Qualifier("gmail") GoogleAuthorizationCodeFlow gmailFlow,
            GmailClientFactory gmailClientFactory,
            UserRepository userRepository,
            JwtService jwtService) {
        this.loginFlow = loginFlow;
        this.gmailFlow = gmailFlow;
        this.gmailClientFactory = gmailClientFactory;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Value("${google.oauth.redirect-uri}")
    private String loginRedirectUri;

    @Value("${google.oauth.gmail-redirect-uri}")
    private String gmailRedirectUri;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Step 1: plain sign-in, no Gmail access requested ────────────────────────

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String url = loginFlow.newAuthorizationUrl()
                .setRedirectUri(loginRedirectUri)
                .set("prompt", "select_account")
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code) throws Exception {
        GoogleTokenResponse tokenResponse = loginFlow.newTokenRequest(code)
                .setRedirectUri(loginRedirectUri)
                .execute();

        GoogleCredential credential = gmailClientFactory.newCredential()
                .setFromTokenResponse(tokenResponse);

        Map<String, Object> profile = fetchGoogleUserInfo(credential);
        String email = (String) profile.get("email");
        String name = (String) profile.get("name");

        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/login?error=no_email"))
                    .build();
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .active(false)
                        .build());

        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        userRepository.save(user);

        log.info("User logged in email={}, name={}, id={}, gmailConnected={}",
                email, name, user.getId(), user.isGmailConnected());

        String jwt = jwtService.issue(user.getId(), email);

        String redirectTarget = frontendUrl + "/auth/callback?token=" + jwt;
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectTarget))
                .build();
    }

    // ── Step 2: connect Gmail, triggered later from the UI by a logged-in user ──

    // Returns the Google consent URL as JSON rather than redirecting directly.
    // A plain <a href> or window.location navigation to a JWT-protected endpoint
    // can't carry an Authorization header, so the frontend instead calls this
    // via an authenticated fetch (header attached normally), then does the
    // actual browser navigation itself using the URL we hand back.
    @GetMapping("/gmail/connect")
    public ResponseEntity<Map<String, String>> connectGmail() {
        UUID userId = SecurityUtils.currentUserId();

        // Short-lived, purpose-scoped token carried as `state` through Google's
        // redirect so the callback below can tell which user is linking Gmail,
        // since that request won't carry our normal Authorization header.
        String state = jwtService.issueGmailLinkState(userId);

        String url = gmailFlow.newAuthorizationUrl()
                .setRedirectUri(gmailRedirectUri)
                .set("prompt", "consent")
                .setState(state)
                .build();

        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/gmail/callback")
    public ResponseEntity<Void> gmailCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) throws Exception {

        if (error != null) {
            log.warn("Gmail link declined or failed: {}", error);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/settings?gmail_error=" + error))
                    .build();
        }

        UUID userId = state != null ? jwtService.extractGmailLinkUserId(state) : null;
        if (userId == null) {
            log.warn("Gmail callback received with missing/invalid/expired state token");
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/settings?gmail_error=invalid_state"))
                    .build();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Gmail callback state referenced unknown userId={}", userId);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/settings?gmail_error=unknown_user"))
                    .build();
        }

        GoogleTokenResponse tokenResponse = gmailFlow.newTokenRequest(code)
                .setRedirectUri(gmailRedirectUri)
                .execute();

        if (tokenResponse.getRefreshToken() == null) {
            // Happens if the user previously granted offline access and Google
            // doesn't re-issue a refresh token without `prompt=consent` — we
            // always force consent above, but guard anyway.
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/settings?gmail_error=no_refresh_token"))
                    .build();
        }

        user.setGmailRefreshToken(tokenResponse.getRefreshToken());
        if (user.isReadyForScanning()) {
            user.setActive(true);
        }
        userRepository.save(user);

        log.info("Gmail connected for userId={}, email={}", user.getId(), user.getEmail());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/settings?gmail=connected"))
                .build();
    }

    private Map<String, Object> fetchGoogleUserInfo(GoogleCredential credential) {
        try {
            com.google.api.client.http.HttpRequestFactory requestFactory =
                    new com.google.api.client.http.javanet.NetHttpTransport()
                            .createRequestFactory(credential);

            com.google.api.client.http.HttpRequest request = requestFactory.buildGetRequest(
                    new com.google.api.client.http.GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo"));

            com.google.api.client.json.JsonObjectParser parser =
                    new com.google.api.client.json.JsonObjectParser(
                            com.google.api.client.json.gson.GsonFactory.getDefaultInstance());

            //noinspection unchecked
            return (Map<String, Object>) parser.parseAndClose(
                    request.execute().getContent(),
                    StandardCharsets.UTF_8,
                    Map.class);
        } catch (Exception e) {
            log.warn("Failed to fetch Google user info: {}", e.getMessage());
            return Map.of();
        }
    }
}
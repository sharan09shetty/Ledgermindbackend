package com.ledgermind.ledgermindbackend.email.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.gmail.Gmail;
import com.ledgermind.ledgermindbackend.email.config.GmailClientFactory;
import com.ledgermind.ledgermindbackend.security.JwtService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

    private final GoogleAuthorizationCodeFlow flow;
    private final GmailClientFactory gmailClientFactory;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String url = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .set("prompt", "consent")
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code) throws Exception {
        GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        if (tokenResponse.getRefreshToken() == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/login?error=no_refresh_token"))
                    .build();
        }

        GoogleCredential credential = gmailClientFactory.newCredential()
                .setFromTokenResponse(tokenResponse);

        Gmail gmail = gmailClientFactory.buildGmail(credential);
        var profile = gmail.users().getProfile("me").execute();
        String email = profile.getEmailAddress();

        String name = fetchGoogleDisplayName(credential);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .active(false)
                        .build());

        user.setGmailRefreshToken(tokenResponse.getRefreshToken());
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        userRepository.save(user);

        log.info("Gmail connected for user email={}, name={}, id={}", email, name, user.getId());

        String jwt = jwtService.issue(user.getId(), email);

        String redirectTarget = frontendUrl + "/auth/callback?token=" + jwt;
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectTarget))
                .build();
    }

    private String fetchGoogleDisplayName(GoogleCredential credential) {
        try {
            com.google.api.client.http.HttpRequestFactory requestFactory =
                    new com.google.api.client.http.javanet.NetHttpTransport()
                            .createRequestFactory(credential);

            com.google.api.client.http.HttpRequest request = requestFactory.buildGetRequest(
                    new com.google.api.client.http.GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo"));

            com.google.api.client.json.JsonObjectParser parser =
                    new com.google.api.client.json.JsonObjectParser(
                            com.google.api.client.json.gson.GsonFactory.getDefaultInstance());

            var response = parser.parseAndClose(
                    request.execute().getContent(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.util.Map.class);

            return (String) response.get("name");
        } catch (Exception e) {
            log.warn("Failed to fetch Google display name: {}", e.getMessage());
            return null;
        }
    }

}
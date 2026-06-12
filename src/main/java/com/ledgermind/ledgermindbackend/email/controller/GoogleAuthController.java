package com.ledgermind.ledgermindbackend.email.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.gmail.Gmail;
import com.ledgermind.ledgermindbackend.email.config.GmailClientFactory;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

    private final GoogleAuthorizationCodeFlow flow;
    private final GmailClientFactory gmailClientFactory;
    private final UserRepository userRepository;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String url = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .set("prompt", "consent") // forces Google to return a refresh token every time
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam("code") String code) throws Exception {

        GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        if (tokenResponse.getRefreshToken() == null) {
            return ResponseEntity.badRequest().body(
                    "No refresh token returned. Revoke LedgerMind's access in your Google " +
                            "account permissions and try connecting again.");
        }

        GoogleCredential credential = gmailClientFactory.newCredential()
                .setFromTokenResponse(tokenResponse);

        Gmail gmail = gmailClientFactory.buildGmail(credential);
        String email = gmail.users().getProfile("me").execute().getEmailAddress();

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .lastEmailSyncTime(LocalDateTime.now())
                        .active(false)
                        .build());

        user.setGmailRefreshToken(tokenResponse.getRefreshToken());
        userRepository.save(user);

        log.info("Connected Gmail for user email={}, id={}", email, user.getId());

        return ResponseEntity.ok("""
                Gmail connected for %s.

                Next steps:
                1. Set your bank: PATCH /users/%s/bank?code=HDFC
                2. Link Telegram: open your bot and send "/start %s"
                """.formatted(email, user.getId(), user.getId()));
    }
}
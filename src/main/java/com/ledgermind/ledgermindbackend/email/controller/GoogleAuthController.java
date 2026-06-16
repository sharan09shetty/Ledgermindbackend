package com.ledgermind.ledgermindbackend.email.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.gmail.Gmail;
import com.ledgermind.ledgermindbackend.email.config.GmailClientFactory;
import com.ledgermind.ledgermindbackend.telegram.config.TelegramProperties;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final TelegramProperties telegramProperties;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

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

        String html = buildOnboardingPage(user.getId(), email, telegramProperties.getBotUsername());

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String buildOnboardingPage(UUID userId, String email, String botUsername) {
        String template = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>LedgerMind Setup</title>
                <style>
                  body { font-family: -apple-system, sans-serif; max-width: 480px; margin: 40px auto; padding: 0 16px; color: #1a1a1a; }
                  h1 { font-size: 22px; }
                  .step { border: 1px solid #e0e0e0; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
                  .step h2 { margin-top: 0; font-size: 16px; }
                  .done { color: #1a7f37; font-weight: 600; }
                  .pending { color: #888; }
                  select, button { font-size: 14px; padding: 8px 12px; border-radius: 6px; }
                  button { background: #2563eb; color: white; border: none; cursor: pointer; }
                  .telegram-btn { display: inline-block; background: #229ED9; color: white; text-decoration: none; padding: 10px 16px; border-radius: 6px; font-weight: 600; margin-top: 8px; }
                  #celebration { display: none; text-align: center; font-size: 18px; margin-top: 24px; }
                </style>
                </head>
                <body>
                  <h1>Gmail connected</h1>
                  <p>Signed in as <strong>__EMAIL__</strong></p>

                  <div class="step">
                    <h2>Step 1 &mdash; Your bank</h2>
                    <p>LedgerMind reads transaction alert emails from your bank.</p>
                    <select id="bankSelect"></select>
                    <button onclick="saveBank()">Save</button>
                    <p id="bankStatus" class="pending">Not set yet</p>
                  </div>

                  <div class="step">
                    <h2>Step 2 &mdash; Link Telegram</h2>
                    <p>You'll get a notification for every transaction here, and can reply to fix categories.</p>
                    <br/>
                    <a class="telegram-btn" href="https://t.me/__BOT_USERNAME__?start=__USER_ID__" target="_blank">Open Telegram &amp; tap Start</a>
                    <p id="telegramStatus" class="pending">Not linked yet</p>
                  </div>

                  <div id="celebration">All set! LedgerMind is now watching your inbox.</div>

                  <script>
                    const userId = "__USER_ID__";

                    async function loadBanks() {
                      const res = await fetch('/banks');
                      const banks = await res.json();
                      const select = document.getElementById('bankSelect');
                      select.innerHTML = '';
                      banks.forEach(b => {
                        const opt = document.createElement('option');
                        opt.value = b.code;
                        opt.textContent = b.name;
                        select.appendChild(opt);
                      });
                    }

                    async function saveBank() {
                      const code = document.getElementById('bankSelect').value;
                      const res = await fetch(`/users/${userId}/bank?code=${code}`, { method: 'PATCH' });
                      if (res.ok) {
                        refreshStatus();
                      } else {
                        document.getElementById('bankStatus').textContent = 'Failed to save \u2014 try again.';
                      }
                    }

                    async function refreshStatus() {
                      const res = await fetch(`/users/${userId}/status`);
                      if (!res.ok) return;
                      const status = await res.json();

                      const bankEl = document.getElementById('bankStatus');
                      if (status.bankName) {
                        bankEl.textContent = `Saved: ${status.bankName}`;
                        bankEl.className = 'done';
                      }

                      const tgEl = document.getElementById('telegramStatus');
                      if (status.telegramLinked) {
                        tgEl.textContent = 'Linked';
                        tgEl.className = 'done';
                      }

                      if (status.active) {
                        document.getElementById('celebration').style.display = 'block';
                      }
                    }

                    loadBanks();
                    refreshStatus();
                    setInterval(refreshStatus, 3000);
                  </script>
                </body>
                </html>
                """;

        return template
                .replace("__EMAIL__", email)
                .replace("__USER_ID__", userId.toString())
                .replace("__BOT_USERNAME__", botUsername);
    }
}
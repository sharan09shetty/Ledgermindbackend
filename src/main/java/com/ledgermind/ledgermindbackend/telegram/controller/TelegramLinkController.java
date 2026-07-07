package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramLinkResponse;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authenticated Telegram linking endpoints for the web UI (as opposed to
 * TelegramWebhookController, which is the public endpoint Telegram itself
 * calls). Protected by the standard JWT filter chain - no path exemption is
 * added for these in SecurityConfig.
 */
@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
public class TelegramLinkController {

    private final TelegramLinkService telegramLinkService;

    @PostMapping("/link-token")
    public ResponseEntity<TelegramLinkResponse> generateLinkToken() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(telegramLinkService.generateLinkToken(userId));
    }

    @PostMapping("/unlink")
    public ResponseEntity<Void> unlink() {
        UUID userId = SecurityUtils.currentUserId();
        telegramLinkService.unlink(userId);
        return ResponseEntity.noContent().build();
    }
}

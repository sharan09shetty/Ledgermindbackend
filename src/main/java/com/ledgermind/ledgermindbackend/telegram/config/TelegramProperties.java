package com.ledgermind.ledgermindbackend.telegram.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "telegram")
@Getter
@Setter
public class TelegramProperties {
    private String botToken;
    private String botUsername;

    /**
     * Shared secret registered with Telegram via setWebhook's secret_token
     * param; Telegram echoes it back on every webhook call in the
     * X-Telegram-Bot-Api-Secret-Token header. Empty = no verification
     * (local dev with mocked Telegram).
     */
    private String webhookSecret;
}
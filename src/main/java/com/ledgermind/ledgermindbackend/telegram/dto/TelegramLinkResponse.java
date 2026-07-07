package com.ledgermind.ledgermindbackend.telegram.dto;

import java.time.LocalDateTime;

public record TelegramLinkResponse(String deepLink, LocalDateTime expiresAt) {
}

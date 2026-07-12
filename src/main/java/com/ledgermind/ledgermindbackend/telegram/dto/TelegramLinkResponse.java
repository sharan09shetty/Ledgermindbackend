package com.ledgermind.ledgermindbackend.telegram.dto;

import java.time.Instant;

/**
 * expiresAt is an Instant (serialized as ISO-8601 with a Z offset) rather
 * than a LocalDateTime: the server's zone-less wall-clock time is ambiguous
 * to browsers in other zones and made fresh links look already expired.
 */
public record TelegramLinkResponse(String deepLink, Instant expiresAt) {
}

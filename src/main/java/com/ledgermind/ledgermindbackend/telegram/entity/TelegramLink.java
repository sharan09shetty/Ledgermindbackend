package com.ledgermind.ledgermindbackend.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks a user's Telegram linking state. One row per user (unique user_id)
 * doubles as both "pending link token" state and "active session" state:
 * - link_token / link_token_expires_at are set while a link is pending and
 *   cleared the moment it's claimed (or never set at all before first use).
 * - chat_id is null until a link is claimed, and is the single active
 *   Telegram chat for that user from then on. Only one row can ever hold a
 *   given chat_id (unique), and only one row exists per user, so a user can
 *   only ever have at most one active Telegram session by construction.
 */
@Entity
@Table(name = "telegram_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "chat_id", unique = true)
    private String chatId;

    @Column(name = "link_token", unique = true)
    private String linkToken;

    @Column(name = "link_token_expires_at")
    private LocalDateTime linkTokenExpiresAt;

    private LocalDateTime linkedAt;

    private LocalDateTime created;

    @PrePersist
    void onCreate() {
        if (created == null) {
            created = LocalDateTime.now();
        }
    }
}

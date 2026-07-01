package com.ledgermind.ledgermindbackend.user.entity;

import com.ledgermind.ledgermindbackend.email.entity.Bank;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private UUID id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "last_email_sync_time")
    private LocalDateTime lastEmailSyncTime;

    @Column(name = "telegram_chat_id", unique = true)
    private String telegramChatId;

    private Boolean active;

    @Column(name = "gmail_refresh_token", columnDefinition = "TEXT")
    private String gmailRefreshToken;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bank_code", referencedColumnName = "code")
    private Bank bank;

    @Transient
    public boolean isGmailConnected() {
        return gmailRefreshToken != null && !gmailRefreshToken.isBlank();
    }

    /**
     * A user is only ready to be picked up by the scan scheduler once they've
     * logged in, connected Gmail, picked a bank, and linked Telegram. Gmail
     * linking now happens as a separate step after login, so this can no
     * longer be inferred just from bank + telegram being set.
     */
    @Transient
    public boolean isReadyForScanning() {
        return isGmailConnected() && bank != null && telegramChatId != null;
    }
}
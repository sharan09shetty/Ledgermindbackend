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

    private Boolean active;

    // NOT NULL in the DB; @Builder.Default keeps builder-created users (e.g.
    // first Google sign-in) from inserting an explicit NULL.
    @Builder.Default
    @Column(nullable = false)
    private Boolean onboarded = false;

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
     * connected Gmail and picked a bank. Telegram linking is optional and
     * independent - it only gates notifications/chat, not scanning itself.
     */
    @Transient
    public boolean isReadyForScanning() {
        return isGmailConnected() && bank != null;
    }
}
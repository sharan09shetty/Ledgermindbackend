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

    @Column(unique = true, nullable = false)
    private String telegramChatId;

    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bank_code",
            referencedColumnName = "code"
    )
    private Bank bank;
}
package com.ledgermind.ledgermindbackend.email.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "raw_emails")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RawEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    @Column(name = "gmail_message_id", unique = true, nullable = false)
    private String gmailMessageId;

    private String subject;

    private String sender;

    @Column(columnDefinition = "TEXT")
    private String body;

    private LocalDateTime receivedAt;

    private Boolean processed;
}
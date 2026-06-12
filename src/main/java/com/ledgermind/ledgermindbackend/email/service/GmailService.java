package com.ledgermind.ledgermindbackend.email.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.ledgermind.ledgermindbackend.email.config.GmailClientFactory;
import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.repository.RawEmailRepository;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ledgermind.ledgermindbackend.email.service.EmailBodyExtractor.extractBody;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailService {

    private final GmailClientFactory gmailClientFactory;
    private final RawEmailRepository rawEmailRepository;
    private final UserRepository userRepository;

    @Transactional
    public void fetchAndSaveEmails(User user) throws Exception {

        if (user.getBank() == null) {
            log.warn("User {} has no bank configured, skipping email scan", user.getId());
            return;
        }

        Gmail gmailClient = gmailClientFactory.buildClientFor(user);

        LocalDateTime lastSyncTime = user.getLastEmailSyncTime();

        String query = "from:" + user.getBank().getSenderEmail();

        if (lastSyncTime != null) {
            long epochSeconds = lastSyncTime.toEpochSecond(ZoneOffset.UTC);
            query += " after:" + epochSeconds;
        }

        ListMessagesResponse response = gmailClient.users()
                .messages()
                .list("me")
                .setQ(query)
                .execute();

        List<Message> messages = response.getMessages();

        if (messages == null || messages.isEmpty()) {
            log.info("No new emails found for user {}", user.getId());
            return;
        }

        LocalDateTime latestProcessedTime = lastSyncTime;
        List<RawEmail> rawEmails = new ArrayList<>();

        for (Message message : messages) {

            if (rawEmailRepository.existsByGmailMessageId(message.getId())) {
                break;
            }

            log.info("Processing email id={} for user={}", message.getId(), user.getId());

            Message fullMessage = gmailClient.users()
                    .messages()
                    .get("me", message.getId())
                    .execute();

            String subject = "";
            String sender = "";

            for (MessagePartHeader header : fullMessage.getPayload().getHeaders()) {
                if (header.getName().equalsIgnoreCase("Subject")) subject = header.getValue();
                if (header.getName().equalsIgnoreCase("From")) sender = header.getValue();
            }

            String body = extractBody(fullMessage);

            if (!isTransactionEmail(body)) {
                continue;
            }

            LocalDateTime receivedAt = convertToLocalDateTime(fullMessage.getInternalDate());

            rawEmails.add(buildRawEmailEntity(user.getId(), message.getId(), subject, sender, body, receivedAt));

            if (latestProcessedTime == null || receivedAt.isAfter(latestProcessedTime)) {
                latestProcessedTime = receivedAt;
            }
        }

        if (!rawEmails.isEmpty()) {
            rawEmailRepository.saveAll(rawEmails);
            log.info("Saved {} emails for user {}", rawEmails.size(), user.getId());
            user.setLastEmailSyncTime(latestProcessedTime);
            userRepository.save(user);
        } else {
            log.info("No new emails to save for user {}", user.getId());
        }
    }

    private RawEmail buildRawEmailEntity(UUID userId, String gmailMessageId, String subject, String sender, String body, LocalDateTime receivedAt) {
        return RawEmail.builder()
                .userId(userId)
                .gmailMessageId(gmailMessageId)
                .subject(subject)
                .sender(sender)
                .body(body)
                .receivedAt(receivedAt)
                .processed(false)
                .build();
    }

    private LocalDateTime convertToLocalDateTime(Long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private boolean isTransactionEmail(String body) {
        String content = body.toLowerCase();
        return content.contains("credited")
                || content.contains("debited")
                || content.contains("upi transaction reference")
                || content.contains("successfully deposited");
    }
}
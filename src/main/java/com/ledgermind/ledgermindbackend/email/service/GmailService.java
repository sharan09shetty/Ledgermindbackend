package com.ledgermind.ledgermindbackend.email.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.ledgermind.ledgermindbackend.email.config.GmailClientFactory;
import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.repository.RawEmailRepository;
import com.ledgermind.ledgermindbackend.queue.publisher.RawEmailSnsPublisher;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
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
    private final RawEmailSnsPublisher snsPublisher;

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
            long epochSeconds = lastSyncTime.plusSeconds(1).toEpochSecond(ZoneOffset.UTC);
            query += " after:" + epochSeconds;
        }

        List<Message> messages = new ArrayList<>();
        String pageToken = null;
        do {
            ListMessagesResponse response = gmailClient.users()
                    .messages()
                    .list("me")
                    .setQ(query)
                    .setPageToken(pageToken)
                    .execute();

            if (response.getMessages() != null) {
                messages.addAll(response.getMessages());
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        if (messages.isEmpty()) {
            log.info("No new emails found for user {}", user.getId());
            return;
        }

        LocalDateTime latestReceivedAt = lastSyncTime;
        List<RawEmail> newEmails = new ArrayList<>();

        for (Message message : messages) {

            if (rawEmailRepository.existsByGmailMessageId(message.getId())) {
                log.debug("Skipping already-saved email gmailId={}", message.getId());
                continue;
            }

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

            newEmails.add(buildRawEmailEntity(user.getId(), message.getId(), subject, sender, body, receivedAt));

            if (latestReceivedAt == null || receivedAt.isAfter(latestReceivedAt)) {
                latestReceivedAt = receivedAt;
            }
        }

        if (latestReceivedAt != null && !latestReceivedAt.equals(lastSyncTime)) {
            user.setLastEmailSyncTime(latestReceivedAt);
            userRepository.save(user);
        }

        if (!newEmails.isEmpty()) {
            List<RawEmail> saved = rawEmailRepository.saveAll(newEmails);
            log.info("Saved {} new emails for user {}", saved.size(), user.getId());
            saved.forEach(email -> snsPublisher.publish(email, user));
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
                .build();
    }

    private LocalDateTime convertToLocalDateTime(Long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private boolean isTransactionEmail(String body) {
        String content = body.toLowerCase();

        if (content.contains("upcoming e-mandate") || content.contains("will be debited")) {
            return false;
        }

        return content.contains("credited")
                || content.contains("debited")
                || content.contains("upi transaction reference")
                || content.contains("successfully deposited");
    }
}
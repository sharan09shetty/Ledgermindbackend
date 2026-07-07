package com.ledgermind.ledgermindbackend.user.service;

import com.ledgermind.ledgermindbackend.email.exception.GmailReauthRequiredException;
import com.ledgermind.ledgermindbackend.email.service.GmailService;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramLinkService;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserScanSchedulerService {

    private final UserRepository userRepository;
    private final GmailService gmailService;
    private final TelegramService telegramService;
    private final TelegramLinkService telegramLinkService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Scheduled(cron = "${ledgermind.scan.cron:0 */15 * * * *}")
    public void scheduledScan() {
        triggerScans();
    }

    public void triggerScans() {
        List<User> users = userRepository.findByActiveTrue();
        log.info("Found {} active users", users.size());

        for (User user : users) {
            try {
                gmailService.fetchAndSaveEmails(user);
            } catch (GmailReauthRequiredException e) {
                log.warn("Gmail reconnection required for user {}: {}", user.getId(), e.getMessage());
                deactivateAndNotify(user);
            } catch (Exception e) {
                log.error("Scan failed for user {}", user.getId(), e);
            }
        }
    }

    private void deactivateAndNotify(User user) {
        user.setActive(false);
        userRepository.save(user);

        Optional<String> chatId = telegramLinkService.resolveChatId(user.getId());
        if (chatId.isPresent()) {
            telegramService.sendMessage(TelegramMessageRequest.builder()
                    .chat_id(chatId.get())
                    .text("LedgerMind lost access to your Gmail account. Please log in and reconnect Gmail here: " + frontendUrl + "/settings")
                    .build());
        }
    }
}
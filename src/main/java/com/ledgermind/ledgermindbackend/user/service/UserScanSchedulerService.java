package com.ledgermind.ledgermindbackend.user.service;

import com.ledgermind.ledgermindbackend.email.service.GmailService;
import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserScanSchedulerService {

    private final UserRepository userRepository;
    private final GmailService gmailService;
    private final TransactionProcessingService transactionProcessingService;

    @Scheduled(fixedDelayString = "${ledgermind.scan.fixed-delay-ms:900000}")
    public void scheduledScan() {
        triggerScans();
    }

    public void triggerScans() {
        List<User> users = userRepository.findByActiveTrue();
        log.info("Found {} active users", users.size());

        for (User user : users) {
            try {
                gmailService.fetchAndSaveEmails(user);
                transactionProcessingService.extractAndProcessTransactions(user.getId());
            } catch (Exception e) {
                // One user's failure (e.g. revoked Gmail access) must never block the others.
                log.error("Scan failed for user {}", user.getId(), e);
            }
        }
    }
}
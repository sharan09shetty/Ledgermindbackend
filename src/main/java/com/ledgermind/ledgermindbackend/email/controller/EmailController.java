package com.ledgermind.ledgermindbackend.email.controller;

import com.ledgermind.ledgermindbackend.email.service.GmailService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import com.ledgermind.ledgermindbackend.user.service.UserScanSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dev-only triggers for exercising the scan pipeline by hand. Only
 * registered under the "local" profile: both endpoints act on arbitrary
 * users (not the caller), so they must never exist in production.
 */
@RestController
@Profile("local")
@RequiredArgsConstructor
public class EmailController {

    private final GmailService gmailService;
    private final UserRepository userRepository;
    private final UserScanSchedulerService userScanSchedulerService;

    @PostMapping("/emails/test/{userId}")
    public void testEmails(@PathVariable UUID userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        gmailService.fetchAndSaveEmails(user);
    }

    /** Runs the scheduled scan for every active user, immediately. */
    @PostMapping("/emails/scan-all")
    public void triggerScan() {
        userScanSchedulerService.triggerScans();
    }
}

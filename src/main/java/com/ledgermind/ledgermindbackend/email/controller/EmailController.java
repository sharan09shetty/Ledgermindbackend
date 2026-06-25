package com.ledgermind.ledgermindbackend.email.controller;

import com.ledgermind.ledgermindbackend.email.service.GmailService;
import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EmailController {

    private final GmailService gmailService;
    private final TransactionProcessingService transactionProcessingService;
    private final UserRepository userRepository;

    @PostMapping("/emails/test/{userId}")
    public void testEmails(@PathVariable UUID userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        gmailService.fetchAndSaveEmails(user);
    }
}
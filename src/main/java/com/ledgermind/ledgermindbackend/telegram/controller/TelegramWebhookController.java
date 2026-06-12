package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramUpdate;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramService telegramService;
    private final TransactionProcessingService transactionProcessingService;
    private final UserRepository userRepository;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(@RequestBody TelegramUpdate update) {
        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText();
        Long messageId = update.getMessage().getMessageId();

        log.info("chatId={}, text={}, messageId={}", chatId, text, messageId);

        if (text != null && text.startsWith("/start")) {
            handleStart(chatId, text);
            return ResponseEntity.ok().build();
        }

        if (update.getMessage().getReplyToMessage() != null) {
            Long repliedMessageId = update.getMessage().getReplyToMessage().getMessageId();
            log.info("Received reply to messageId={}", repliedMessageId);
            transactionProcessingService.updateTransactionCategory(chatId, repliedMessageId, text);
        } else {
            sendReplyMessage(chatId, "Please reply to a transaction message with the category you want to set. For example, if you want to categorize a transaction as 'Food', reply to the transaction message with 'Food'.");
        }

        return ResponseEntity.ok().build();
    }

    private void handleStart(Long chatId, String text) {
        String[] parts = text.trim().split("\\s+");

        if (parts.length < 2) {
            sendReplyMessage(chatId, "Welcome to LedgerMind! Connect Gmail first, then send /start <your-user-id> here using the code we gave you.");
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            sendReplyMessage(chatId, "That doesn't look like a valid linking code.");
            return;
        }

        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            sendReplyMessage(chatId, "We couldn't find an account for that code. Please connect Gmail first.");
            return;
        }

        User user = userOpt.get();
        user.setTelegramChatId(chatId.toString());

        if (user.getBank() != null) {
            user.setActive(true);
        }

        userRepository.save(user);

        sendReplyMessage(chatId, "Telegram linked! You'll get transaction notifications here once scans run.");
    }

    public void sendReplyMessage(Long chatId, String text) {
        telegramService.sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text(text)
                .build());
    }
}
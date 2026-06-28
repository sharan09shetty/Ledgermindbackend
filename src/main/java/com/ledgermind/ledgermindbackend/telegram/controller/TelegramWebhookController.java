package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.telegram.advisor.FinancialAdvisorService;
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
    private final FinancialAdvisorService financialAdvisorService;

    private static final String ASK_PREFIX = "/ask";

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(@RequestBody TelegramUpdate update) {
        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText();
        Long messageId = update.getMessage().getMessageId();

        log.info("chatId={}, text={}, messageId={}", chatId, text, messageId);

        if (text == null) {
            return ResponseEntity.ok().build();
        }

        // /start <userId> — link Telegram account
        if (text.startsWith("/start")) {
            handleStart(chatId, text);
            return ResponseEntity.ok().build();
        }

        // /ask <question> — financial advisor
        if (text.startsWith(ASK_PREFIX)) {
            handleAsk(chatId, text);
            return ResponseEntity.ok().build();
        }

        // Reply to a transaction message — category correction
        if (update.getMessage().getReplyToMessage() != null) {
            Long repliedMessageId = update.getMessage().getReplyToMessage().getMessageId();
            log.info("Received category correction reply to messageId={}", repliedMessageId);
            transactionProcessingService.updateTransactionCategory(chatId, repliedMessageId, text);
            return ResponseEntity.ok().build();
        }

        sendReplyMessage(chatId, """
                Here's what you can do:
                • Reply to any transaction message to correct its category
                • Use /ask <question> to ask about your finances
                  e.g. /ask how much did I spend on food this month?
                """);

        return ResponseEntity.ok().build();
    }

    private void handleAsk(Long chatId, String text) {
        String question = text.substring(ASK_PREFIX.length()).trim();

        if (question.isEmpty()) {
            sendReplyMessage(chatId, "Please ask me something! e.g. /ask how much did I spend this month?");
            return;
        }

        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId.toString());

        if (userOpt.isEmpty()) {
            sendReplyMessage(chatId, "Your Telegram isn't linked yet. Please use /start <your-user-id> first.");
            return;
        }

        telegramService.sendChatAction(chatId, "typing");

        String response = financialAdvisorService.ask(userOpt.get().getId(), question);
        sendReplyMessage(chatId, response);
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
        sendReplyMessage(chatId, "Telegram linked! You'll get transaction notifications here. Use /ask to query your finances.");
    }

    public void sendReplyMessage(Long chatId, String text) {
        telegramService.sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text(text)
                .build());
    }
}
package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.ai.service.CashTransactionParser;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramService telegramService;
    private final TransactionProcessingService transactionProcessingService;
    private final UserRepository userRepository;
    private final FinancialAdvisorService financialAdvisorService;
    private final CashTransactionParser cashTransactionParser;

    private static final String ASK_PREFIX = "/ask";
    private static final String LOG_PREFIX = "/log";

    // Tracks which chatIds are waiting for /ask input and /log input
    private final ConcurrentHashMap<Long, Boolean> awaitingQuestion = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> awaitingCashLog = new ConcurrentHashMap<>();

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(@RequestBody TelegramUpdate update) {
        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText();
        Long messageId = update.getMessage().getMessageId();

        log.info("chatId={}, text={}, messageId={}", chatId, text, messageId);

        if (text == null) return ResponseEntity.ok().build();

        // /start <userId>
        if (text.startsWith("/start")) {
            handleStart(chatId, text);
            return ResponseEntity.ok().build();
        }

        // /ask <question> or /ask alone
        if (text.startsWith(ASK_PREFIX)) {
            String question = text.substring(ASK_PREFIX.length()).trim();
            if (question.isEmpty()) {
                awaitingQuestion.put(chatId, true);
                sendReplyMessage(chatId, "What would you like to know about your finances?");
            } else {
                handleAsk(chatId, question);
            }
            return ResponseEntity.ok().build();
        }

        // /log alone (from menu) or /log <description>
        if (text.startsWith(LOG_PREFIX)) {
            String description = text.substring(LOG_PREFIX.length()).trim();
            if (description.isEmpty()) {
                awaitingCashLog.put(chatId, true);
                sendReplyMessage(chatId, """
                        Describe your cash transaction naturally:
                        
                        e.g. "spent 200 on auto" or "paid 500 at medical shop" or "received 1000 from Rahul"
                        """);
            } else {
                handleCashLog(chatId, description);
            }
            return ResponseEntity.ok().build();
        }

        // Awaiting /ask follow-up
        if (awaitingQuestion.getOrDefault(chatId, false)) {
            awaitingQuestion.remove(chatId);
            handleAsk(chatId, text);
            return ResponseEntity.ok().build();
        }

        // Awaiting /log follow-up
        if (awaitingCashLog.getOrDefault(chatId, false)) {
            awaitingCashLog.remove(chatId);
            handleCashLog(chatId, text);
            return ResponseEntity.ok().build();
        }

        // Reply to a transaction — category correction
        if (update.getMessage().getReplyToMessage() != null) {
            Long repliedMessageId = update.getMessage().getReplyToMessage().getMessageId();
            log.info("Received category correction reply to messageId={}", repliedMessageId);
            transactionProcessingService.updateTransactionCategory(chatId, repliedMessageId, text);
            return ResponseEntity.ok().build();
        }

        // Fallback
        sendReplyMessage(chatId, """
                Here's what you can do:
                • /ask — ask anything about your finances
                • /log — log a cash transaction
                • Reply to any transaction message to correct its category
                """);

        return ResponseEntity.ok().build();
    }

    private void handleAsk(Long chatId, String question) {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId.toString());
        if (userOpt.isEmpty()) {
            sendReplyMessage(chatId, "Your Telegram isn't linked yet. Please use /start <your-user-id> first.");
            return;
        }
        telegramService.sendChatAction(chatId, "typing");
        String response = financialAdvisorService.ask(userOpt.get().getId(), question);
        sendReplyMessage(chatId, response);
    }

    private void handleCashLog(Long chatId, String description) {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId.toString());
        if (userOpt.isEmpty()) {
            sendReplyMessage(chatId, "Your Telegram isn't linked yet. Please use /start <your-user-id> first.");
            return;
        }

        telegramService.sendChatAction(chatId, "typing");

        Transaction transaction = cashTransactionParser.parse(userOpt.get().getId(), description);

        if (transaction == null) {
            sendReplyMessage(chatId, "Sorry, I couldn't understand that. Please try again.\ne.g. \"spent 200 on auto\" or \"paid 500 at medical shop\"");
            return;
        }

        String counterparty = transaction.getCounterparty() != null
                ? " · " + transaction.getCounterparty()
                : "";

        sendReplyMessage(chatId, String.format(
                "✅ Logged ₹%s %s · %s%s\n\nReply to this message to correct the category if needed.",
                transaction.getAmount(),
                transaction.getTransactionType(),
                transaction.getCategory(),
                counterparty
        ));
    }

    private void handleStart(Long chatId, String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            sendReplyMessage(chatId, "Welcome to LedgerMind! Connect Gmail first, then send /start <your-user-id>.");
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
        if (user.getBank() != null) user.setActive(true);
        userRepository.save(user);

        sendReplyMessage(chatId, "Telegram linked! You'll get transaction notifications here.\n\nUse /ask to query your finances or /log to record a cash transaction.");
    }

    public void sendReplyMessage(Long chatId, String text) {
        telegramService.sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text(text)
                .build());
    }
}

package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.ai.service.CashTransactionParser;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.telegram.advisor.FinancialAdvisorService;
import com.ledgermind.ledgermindbackend.telegram.config.TelegramProperties;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramUpdate;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramLinkService;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramService telegramService;
    private final TelegramLinkService telegramLinkService;
    private final TransactionProcessingService transactionProcessingService;
    private final FinancialAdvisorService financialAdvisorService;
    private final CashTransactionParser cashTransactionParser;
    private final TelegramProperties telegramProperties;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(
            @RequestBody TelegramUpdate update,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken) {

        // The webhook is permitAll in SecurityConfig (Telegram's servers call
        // it), so the secret_token registered via setWebhook is the only
        // thing proving a request really came from Telegram.
        if (!isValidSecret(secretToken)) {
            log.warn("Rejected webhook call with missing/invalid secret token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Updates without a message (edited messages, channel posts,
        // callback queries...) aren't handled - ack them so Telegram
        // doesn't retry.
        if (update == null || update.getMessage() == null || update.getMessage().getChat() == null) {
            return ResponseEntity.ok().build();
        }

        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText();
        Long messageId = update.getMessage().getMessageId();

        // /start carries a one-time link token - never log it in plaintext,
        // even though it's already single-use and short-lived, log access
        // shouldn't be an easy way to steal a still-valid link.
        String logText = (text != null && text.startsWith("/start")) ? "/start <redacted>" : text;
        log.info("chatId={}, text={}, messageId={}", chatId, logText, messageId);

        if (text == null) return ResponseEntity.ok().build();

        if (text.startsWith("/start")) {
            handleStart(chatId, text);
            return ResponseEntity.ok().build();
        }

        if (text.startsWith("/log")) {
            String description = text.substring("/log".length()).trim();
            if (description.isEmpty()) {
                sendReplyMessage(chatId, "Describe your cash transaction:\ne.g. \"spent 200 on auto\" or \"received 1000 from Rahul\"");
            } else {
                handleCashLog(chatId, description);
            }
            return ResponseEntity.ok().build();
        }

        if (text.startsWith("/forget")) {
            handleForget(chatId);
            return ResponseEntity.ok().build();
        }

        if (update.getMessage().getReplyToMessage() != null) {
            Long repliedMessageId = update.getMessage().getReplyToMessage().getMessageId();
            transactionProcessingService.updateTransactionCategory(chatId, repliedMessageId, text);
            return ResponseEntity.ok().build();
        }

        handleAsk(chatId, text);
        return ResponseEntity.ok().build();
    }

    private void handleAsk(Long chatId, String question) {
        Optional<User> userOpt = telegramLinkService.resolveUserByChatId(chatId.toString());
        if (userOpt.isEmpty()) {
            sendReplyMessage(chatId, "Your Telegram isn't linked yet. Please connect it from the LedgerMind app first.");
            return;
        }
        telegramService.sendChatAction(chatId, "typing");
        String response = financialAdvisorService.ask(chatId, userOpt.get().getId(), question);
        if (response == null || response.isBlank()) {
            log.warn("[Advisor] Empty response for chatId={} question={}", chatId, question);
            sendReplyMessage(chatId, "Sorry, I couldn't generate a response. Please try rephrasing your question.");
            return;
        }
        sendReplyMessage(chatId, response);
    }

    private void handleCashLog(Long chatId, String description) {
        Optional<User> userOpt = telegramLinkService.resolveUserByChatId(chatId.toString());
        if (userOpt.isEmpty()) {
            sendReplyMessage(chatId, "Your Telegram isn't linked yet. Please connect it from the LedgerMind app first.");
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

    /**
     * Handles /start <token>. The token is a random, single-use, short-lived
     * credential generated by the authenticated web UI (POST
     * /telegram/link-token) - never the user's raw userId. See
     * TelegramLinkService#claimToken for the atomic claim + single-session
     * enforcement logic.
     */
    private void handleStart(Long chatId, String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            sendReplyMessage(chatId, "Welcome to LedgerMind! Open the app and tap \"Connect Telegram\" to link your account.");
            return;
        }

        String token = parts[1];
        TelegramLinkService.ClaimResult result = telegramLinkService.claimToken(token, chatId.toString());

        switch (result.status()) {
            case SUCCESS -> sendReplyMessage(chatId,
                    "Telegram linked! You'll get transaction notifications here.\n\nUse /ask to query your finances or /log to record a cash transaction.");
            case CHAT_ALREADY_LINKED -> sendReplyMessage(chatId,
                    "This Telegram account is already linked to a different LedgerMind account. Unlink it there first if you want to relink.");
            case INVALID_OR_EXPIRED -> sendReplyMessage(chatId,
                    "This link is invalid or has expired. Please generate a new one from the app.");
        }
    }

    private void handleForget(Long chatId) {
        financialAdvisorService.clearMemory(chatId);
        sendReplyMessage(chatId, "Conversation history cleared. Starting fresh!");
    }

    /**
     * Constant-time comparison against the configured webhook secret. An
     * empty/unset secret disables verification - only acceptable for local
     * dev against the mock Telegram API.
     */
    private boolean isValidSecret(String secretToken) {
        String expected = telegramProperties.getWebhookSecret();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (secretToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                secretToken.getBytes(StandardCharsets.UTF_8));
    }

    public void sendReplyMessage(Long chatId, String text) {
        telegramService.sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text(text)
                .build());
    }
}
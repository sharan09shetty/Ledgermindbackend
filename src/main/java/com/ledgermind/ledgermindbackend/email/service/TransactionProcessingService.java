package com.ledgermind.ledgermindbackend.email.service;

import com.ledgermind.ledgermindbackend.email.entity.MerchantCategoryMapping;
import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.enums.PaymentMode;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import com.ledgermind.ledgermindbackend.email.repository.MerchantCategoryMappingRepository;
import com.ledgermind.ledgermindbackend.email.repository.RawEmailRepository;
import com.ledgermind.ledgermindbackend.email.repository.TransactionRepository;
import com.ledgermind.ledgermindbackend.queue.dto.RawEmailMessage;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramLinkService;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionProcessingService {

    private final RawEmailRepository rawEmailRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantCategoryMappingRepository categoryMappingRepository;
    private final List<TransactionParser> parsers;
    private final CategorizationService categorizationService;
    private final TelegramService telegramService;
    private final TelegramLinkService telegramLinkService;

    private Optional<TransactionParser> getParser(RawEmail email) {
        return parsers.stream().filter(p -> p.supports(email)).findFirst();
    }

    public void processSingleEmail(RawEmailMessage message) {
        RawEmail email = RawEmail.builder()
                .id(message.rawEmailId())
                .userId(message.userId())
                .sender(message.sender())
                .body(message.body())
                .receivedAt(message.receivedAt())
                .build();

        processSingleEmail(email, message.telegramChatId());
    }

    private void processSingleEmail(RawEmail email, String telegramChatId) {
        Optional<TransactionParser> parser = getParser(email);

        if (parser.isEmpty()) {
            log.warn("No parser found for email sender={}.", email.getSender());
            throw new RuntimeException("No parser found for sender: " + email.getSender());
        }

        Transaction transaction = parser.get().parse(email);
        transaction.setRawEmailId(email.getId());
        Category category = categorizationService.categorize(transaction);
        transaction.setCategory(category);
        transactionRepository.save(transaction);

        if (telegramChatId == null) {
            log.info("User {} has no Telegram chat linked yet, skipping notification", email.getUserId());
            return;
        }

        try {
            Long messageId = telegramService.sendTransactionNotification(telegramChatId, transaction);
            transaction.setTelegramMessageId(messageId);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification for transaction id={}", transaction.getId(), e);
        }
    }

    public void updateTransactionCategory(Long chatId, Long messageId, String newCategory) {

        User user = telegramLinkService.resolveUserByChatId(chatId.toString()).orElse(null);

        if (user == null) {
            log.warn("Received Telegram update from unlinked chatId={}", chatId);
            return;
        }

        Category category;
        try {
            category = Category.valueOf(newCategory.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category received from telegram: {}", newCategory);
            telegramService.sendMessage(TelegramMessageRequest.builder()
                    .chat_id(chatId.toString())
                    .text("Invalid category " + newCategory + ". Please use one of the following categories: " + Arrays.toString(Category.values()))
                    .build());
            return;
        }

        Transaction transaction = transactionRepository.findByTelegramMessageIdAndUserId(messageId, user.getId());

        if (transaction == null) {
            log.warn("No transaction found for user={}, messageId={}", user.getId(), messageId);
            return;
        }

        transaction.setCategory(category);
        transactionRepository.save(transaction);

        categoryMappingRepository.save(MerchantCategoryMapping.builder()
                .merchant(transaction.getCounterparty())
                .category(category)
                .build());

        log.info("Updated category for merchant={} to {} (user={})", transaction.getCounterparty(), category, user.getId());

        telegramService.sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text("Category for transaction with " + transaction.getCounterparty() + " updated to " + category)
                .build());
    }

    /**
     * Creates a transaction entered manually by the user (as opposed to one
     * parsed from an email). Unrecognized enum values fall back to sane
     * defaults rather than failing the request, matching the previous
     * controller behavior.
     */
    public Transaction createManualTransaction(UUID userId, BigDecimal amount, String transactionTypeRaw,
                                               String categoryRaw, String counterparty, String paymentModeRaw,
                                               String transactionTimeRaw) {

        TransactionType type = parseEnumOrDefault(TransactionType.class, transactionTypeRaw, TransactionType.DEBIT);
        Category category = parseEnumOrDefault(Category.class, categoryRaw, Category.OTHER);
        PaymentMode paymentMode = parseEnumOrDefault(PaymentMode.class, paymentModeRaw, PaymentMode.UNKNOWN);

        LocalDateTime transactionTime;
        try {
            transactionTime = transactionTimeRaw != null
                    ? LocalDateTime.parse(transactionTimeRaw)
                    : LocalDateTime.now();
        } catch (Exception e) {
            transactionTime = LocalDateTime.now();
        }

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(amount)
                .transactionType(type)
                .paymentMode(paymentMode)
                .category(category)
                .counterparty(counterparty)
                .transactionTime(transactionTime)
                .created(LocalDateTime.now())
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * Applies a partial update to an existing transaction (e.g. from the UI's
     * edit-transaction screen). Only non-blank fields are updated; the rest
     * of the transaction is left untouched. Unlike manual creation, invalid
     * enum values here are rejected with a 400 rather than silently
     * defaulted, since this is a deliberate user edit.
     */
    public Transaction updateTransaction(UUID userId, UUID transactionId, String counterparty,
                                         String paymentModeRaw, String categoryRaw) {

        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found: " + transactionId));

        if (counterparty != null && !counterparty.isBlank()) {
            transaction.setCounterparty(counterparty.trim());
        }

        if (paymentModeRaw != null && !paymentModeRaw.isBlank()) {
            transaction.setPaymentMode(parseEnumStrict(PaymentMode.class, paymentModeRaw));
        }

        if (categoryRaw != null && !categoryRaw.isBlank()) {
            Category category = parseEnumStrict(Category.class, categoryRaw);
            transaction.setCategory(category);
        }

        Transaction saved = transactionRepository.save(transaction);
        log.info("Updated transaction id={} (user={})", saved.getId(), userId);
        return saved;
    }

    public void deleteTransaction(UUID transactionId) {
        transactionRepository.deleteById(transactionId);
        log.info("Deleted transaction id={})", transactionId);
    }

    private <E extends Enum<E>> E parseEnumOrDefault(Class<E> enumClass, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnumStrict(Class<E> enumClass, String raw) {
        try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid value '" + raw + "' for " + enumClass.getSimpleName()
                            + ". Allowed values: " + Arrays.toString(enumClass.getEnumConstants()));
        }
    }
}
package com.ledgermind.ledgermindbackend.email.service;

import com.ledgermind.ledgermindbackend.email.entity.MerchantCategoryMapping;
import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.repository.MerchantCategoryMappingRepository;
import com.ledgermind.ledgermindbackend.email.repository.RawEmailRepository;
import com.ledgermind.ledgermindbackend.email.repository.TransactionRepository;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final UserRepository userRepository;
    private final List<TransactionParser> parsers;
    private final CategorizationService categorizationService;
    private final TelegramService telegramService;

    private Optional<TransactionParser> getParser(RawEmail email) {
        return parsers.stream().filter(p -> p.supports(email)).findFirst();
    }

    public void extractAndProcessTransactions(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        while (true) {
            List<RawEmail> emails = rawEmailRepository
                    .findTop10ByProcessedFalseAndUserIdOrderByReceivedAtAsc(userId);

            if (emails.isEmpty()) {
                log.info("No unprocessed emails for user {}", userId);
                break;
            }

            log.info("Processing batch of {} emails for user {}", emails.size(), userId);

            for (RawEmail email : emails) {
                try {
                    processSingleEmail(email, user);
                } catch (Exception e) {
                    log.error("Failed to process email id={} for user={}, marking as processed",
                            email.getId(), userId, e);
                    email.setProcessed(true);
                    rawEmailRepository.save(email);
                }
            }
        }
    }

    private void processSingleEmail(RawEmail email, User user) {
        Optional<TransactionParser> parser = getParser(email);

        if (parser.isEmpty()) {
            log.warn("No parser found for email id={}, sender={}. Marking as processed.",
                    email.getId(), email.getSender());
            email.setProcessed(true);
            rawEmailRepository.save(email);
            return;
        }

        Transaction transaction = parser.get().parse(email);
        Category category = categorizationService.categorize(transaction);
        transaction.setCategory(category);

        transactionRepository.save(transaction);
        email.setProcessed(true);
        rawEmailRepository.save(email);

        if (user.getTelegramChatId() == null) {
            log.info("User {} has no Telegram chat linked yet, skipping notification", user.getId());
            return;
        }

        try {
            Long messageId = telegramService.sendTransactionNotification(user.getTelegramChatId(), transaction);
            transaction.setTelegramMessageId(messageId);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Failed to send telegram notification for transaction id={}", transaction.getId(), e);
        }
    }

    public void updateTransactionCategory(Long chatId, Long messageId, String newCategory) {

        User user = userRepository.findByTelegramChatId(chatId.toString()).orElse(null);

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

        // Scoped by userId — previously this looked up by telegramMessageId alone,
        // which can collide across different users' chats (Telegram message IDs
        // are sequential per-chat, not globally unique).
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
}
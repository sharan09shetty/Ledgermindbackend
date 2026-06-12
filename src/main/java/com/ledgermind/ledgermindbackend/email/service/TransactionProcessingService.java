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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    private final String TELEGRAM_CHAT_ID = "1335526793";

    private Optional<TransactionParser> getParser(RawEmail email) {
        return parsers.stream()
                .filter(parser -> parser.supports(email))
                .findFirst();
    }

    public void extractAndProcessTransactions() {
        while (true) {
            List<RawEmail> emails = rawEmailRepository.findTop10ByProcessedFalseOrderByReceivedAtAsc();
            if (emails.isEmpty()) {
                log.info("No unprocessed emails found, stopping extraction");
                break;
            }
            log.info("Processing batch of {} emails", emails.size());

            for (RawEmail email : emails) {
                try {
                    processSingleEmail(email);
                } catch (Exception e) {
                    log.error("Failed to process email id={}, marking as processed", email.getId(), e);
                    email.setProcessed(true);
                    rawEmailRepository.save(email);
                }
            }
        }
    }

    private void processSingleEmail(RawEmail email) {
        Optional<TransactionParser> parser = getParser(email);

        if (parser.isEmpty()) {
            log.error("No parser found for email id={}, sender={}. Marking as processed.",
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

        try {
            Long messageId = telegramService.sendTransactionNotification(TELEGRAM_CHAT_ID, transaction);
            transaction.setTelegramMessageId(messageId);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Failed to send telegram notification for transaction id={}", transaction.getId(), e);
        }
    }

    public void updateTransactionCategory(Long chatId, Long messageId, String newCategory) {
        Category category;
        try {
            category = Category.valueOf(newCategory.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category received from telegram: {}", newCategory);
            TelegramMessageRequest reply = TelegramMessageRequest.builder()
                    .chat_id(chatId.toString())
                    .text("Invalid category " + newCategory + ". Please use one of the following categories: " + Arrays.toString(Category.values()))
                    .build();
            telegramService.sendMessage(reply);
            return;
        }
        Transaction transaction = transactionRepository.findByTelegramMessageId(messageId);

        if (transaction == null) {
            log.warn("No transaction found for Telegram messageId={}", messageId);
            return;
        }
        transaction.setCategory(category);
        transactionRepository.save(transaction);
        MerchantCategoryMapping mapping = MerchantCategoryMapping.builder()
                .merchant(transaction.getCounterparty())
                .category(category)
                .build();

        categoryMappingRepository.save(mapping);
        log.info("Updated category for merchant={} to {}", transaction.getCounterparty(), category);
        TelegramMessageRequest reply = TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text("Category for transaction with " + transaction.getCounterparty() + " updated to " + category)
                .build();
        telegramService.sendMessage(reply);
    }
}

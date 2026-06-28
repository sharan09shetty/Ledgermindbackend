package com.ledgermind.ledgermindbackend.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.enums.PaymentMode;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import com.ledgermind.ledgermindbackend.email.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashTransactionParser {

    private final ChatClient chatClient;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    private static final String PARSE_PROMPT = """
            You are a financial transaction parser for an Indian user.
            Parse the following natural language description into a structured transaction.
            
            Description: "%s"
            
            Rules:
            - amount: extract the numeric amount in INR. If currency mentioned (₹, Rs, rupees), strip it.
            - transactionType: DEBIT if money was spent/paid/sent. CREDIT if money was received/earned.
            - category: pick the best match from: FOOD, TRAVEL, ENTERTAINMENT, SHOPPING, BILLS, INVESTMENT, SALARY, TRANSFER, HEALTH, OTHER
            - counterparty: the merchant, person, or place. Use null if not mentioned.
            
            Respond ONLY with a valid JSON object, no explanation, no markdown:
            {"amount": 500, "transactionType": "DEBIT", "category": "FOOD", "counterparty": "Darshini"}
            """;

    /**
     * Parses a natural language description into a saved cash Transaction.
     * Returns the saved transaction, or null if parsing fails.
     */
    public Transaction parse(UUID userId, String description) {
        log.info("[CashLog] Parsing description for userId={}: {}", userId, description);

        try {
            String response = chatClient.prompt()
                    .user(PARSE_PROMPT.formatted(description))
                    .call()
                    .content();

            log.info("[CashLog] LLM response: {}", response);

            // Strip markdown fences if model wraps in ```json ... ```
            String cleaned = response.replaceAll("```json|```", "").trim();
            CashTransactionParseResult result = objectMapper.readValue(cleaned, CashTransactionParseResult.class);

            return buildAndSave(userId, result);

        } catch (Exception e) {
            log.error("[CashLog] Failed to parse cash transaction for userId={}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    private Transaction buildAndSave(UUID userId, CashTransactionParseResult result) {
        TransactionType type;
        try {
            type = TransactionType.valueOf(result.transactionType().trim().toUpperCase());
        } catch (Exception e) {
            log.warn("[CashLog] Invalid transactionType '{}', defaulting to DEBIT", result.transactionType());
            type = TransactionType.DEBIT;
        }

        Category category;
        try {
            category = Category.valueOf(result.category().trim().toUpperCase());
        } catch (Exception e) {
            log.warn("[CashLog] Invalid category '{}', defaulting to OTHER", result.category());
            category = Category.OTHER;
        }

        BigDecimal amount = result.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[CashLog] Invalid amount '{}', aborting save", amount);
            return null;
        }

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(amount)
                .transactionType(type)
                .paymentMode(PaymentMode.CASH)
                .category(category)
                .counterparty(result.counterparty())
                .transactionTime(LocalDateTime.now())
                .created(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("[CashLog] Saved cash transaction id={} amount={} type={} category={} counterparty={}",
                saved.getId(), saved.getAmount(), saved.getTransactionType(), saved.getCategory(), saved.getCounterparty());
        return saved;
    }
}

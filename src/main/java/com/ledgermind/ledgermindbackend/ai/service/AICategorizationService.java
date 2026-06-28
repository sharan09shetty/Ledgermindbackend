package com.ledgermind.ledgermindbackend.ai.service;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AICategorizationService {

    private final ChatClient chatClient;

    public Category categorize(Transaction transaction) {
        String response = "";
        try {
            response = chatClient.prompt()
                    .user(buildPrompt(transaction))
                    .call()
                    .content();
            log.info("Categorization response for merchant={} => {}", transaction.getCounterparty(), response);
            return Category.valueOf(response.trim().toUpperCase());
        } catch (Exception e) {
            log.error("Failed to categorize transaction for merchant={}, response='{}'",
                    transaction.getCounterparty(), response, e);
            return Category.OTHER;
        }
    }

    private String buildPrompt(Transaction transaction) {
        return """
                You are a personal finance categorization assistant.
                
                Merchant: %s
                Transaction Type: %s
                Amount: %s
                
                Categories:
                FOOD, TRAVEL, ENTERTAINMENT, SHOPPING, BILLS,
                INVESTMENT, SALARY, TRANSFER, HEALTH, OTHER
                
                Return ONLY the category name, nothing else.
                """.formatted(
                transaction.getCounterparty(),
                transaction.getTransactionType(),
                transaction.getAmount());
    }
}

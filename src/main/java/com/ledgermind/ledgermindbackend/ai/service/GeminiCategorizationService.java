package com.ledgermind.ledgermindbackend.ai.service;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "ai.provider",
        havingValue = "gemini"
)
public class GeminiCategorizationService implements AICategorizationService {

    private final ChatClient chatClient;

    @Override
    public Category categorize(Transaction transaction) {
        log.debug("Categorizing transaction for merchant={}", transaction.getCounterparty());
        String response = "";
        try {
            String prompt = buildPrompt(transaction);
            response = chatClient.prompt().user(prompt).call().content();
            log.info("Gemini response for merchant={} => {}", transaction.getCounterparty(), response);
            return Category.valueOf(response.trim().toUpperCase());
        } catch (Exception e) {
            log.error("Exception occurred while categorizing transaction: {}", response, e);
            return Category.OTHER;
        }
    }

    public String buildPrompt(Transaction transaction) {
        return """
                You are a personal finance categorization assistant.
                
                Merchant: %s
                Transaction Type: %s
                Amount: %s
                
                Categories:
                FOOD
                TRAVEL
                ENTERTAINMENT
                SHOPPING
                BILLS
                INVESTMENT
                SALARY
                TRANSFER
                HEALTH
                OTHER
                
                Return ONLY the category name.
                """
                .formatted(
                        transaction.getCounterparty(),
                        transaction.getTransactionType(),
                        transaction.getAmount()
                );
    }
}
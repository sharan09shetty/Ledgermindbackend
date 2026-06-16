package com.ledgermind.ledgermindbackend.ai.service;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
        name = "ai.provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockCategorizationService implements AICategorizationService {

    @Override
    public Category categorize(Transaction transaction) {
        log.info("Mock categorization for merchant: {}, amount: {}", transaction.getCounterparty(), transaction.getAmount());
        return Category.OTHER;
    }
}

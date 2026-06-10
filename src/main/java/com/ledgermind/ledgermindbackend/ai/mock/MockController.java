package com.ledgermind.ledgermindbackend.ai.mock;

import com.ledgermind.ledgermindbackend.ai.service.GeminiCategorizationService;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/ai")
public class MockController {

    private final GeminiCategorizationService geminiCategorizationService;

    @PostMapping("/test")
    public String test(@RequestParam String counterparty, @RequestParam BigDecimal amount, @RequestParam TransactionType type) {

        Transaction transaction = Transaction.builder()
                        .counterparty(counterparty)
                        .amount(amount)
                        .transactionType(type)
                        .build();

        return geminiCategorizationService.categorize(transaction).name();
    }
}

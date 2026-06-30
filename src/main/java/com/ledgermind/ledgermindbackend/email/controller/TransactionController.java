package com.ledgermind.ledgermindbackend.email.controller;

import com.ledgermind.ledgermindbackend.analytics.dto.TransactionResponse;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.enums.PaymentMode;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import com.ledgermind.ledgermindbackend.email.repository.TransactionRepository;
import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository transactionRepository;

    @PostMapping("/manual")
    public ResponseEntity<TransactionResponse> logManual(
            @RequestBody ManualTransactionRequest request) {

        UUID userId = SecurityUtils.currentUserId();

        Category category;
        try {
            category = Category.valueOf(request.category().trim().toUpperCase());
        } catch (Exception e) {
            category = Category.OTHER;
        }

        TransactionType type;
        try {
            type = TransactionType.valueOf(request.transactionType().trim().toUpperCase());
        } catch (Exception e) {
            type = TransactionType.DEBIT;
        }

        PaymentMode paymentMode;
        try {
            paymentMode = PaymentMode.valueOf(request.paymentMode().trim().toUpperCase());
        } catch (Exception e) {
            paymentMode = PaymentMode.UNKNOWN;
        }

        LocalDateTime transactionTime;
        try {
            transactionTime = request.transactionTime() != null
                    ? LocalDateTime.parse(request.transactionTime())
                    : LocalDateTime.now();
        } catch (Exception e) {
            transactionTime = LocalDateTime.now();
        }

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(request.amount())
                .transactionType(type)
                .paymentMode(paymentMode)
                .category(category)
                .counterparty(request.counterparty())
                .transactionTime(transactionTime)
                .created(LocalDateTime.now())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        return ResponseEntity.ok(TransactionResponse.builder()
                .id(saved.getId())
                .amount(saved.getAmount())
                .transactionType(saved.getTransactionType())
                .paymentMode(saved.getPaymentMode())
                .category(saved.getCategory())
                .counterparty(saved.getCounterparty())
                .transactionTime(saved.getTransactionTime())
                .build());
    }

    public record ManualTransactionRequest(
            BigDecimal amount,
            String transactionType,   // DEBIT or CREDIT
            String category,
            String counterparty,      // nullable
            String paymentMode,       // UPI, CASH, CREDIT_CARD, CHEQUE, etc
            String transactionTime    // ISO datetime string, nullable (defaults to now)
    ) {
    }
}


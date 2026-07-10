package com.ledgermind.ledgermindbackend.email.controller;

import com.ledgermind.ledgermindbackend.analytics.dto.TransactionResponse;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionProcessingService transactionProcessingService;

    @PostMapping("/manual")
    public ResponseEntity<TransactionResponse> logManual(
            @RequestBody ManualTransactionRequest request) {

        UUID userId = SecurityUtils.currentUserId();

        Transaction saved = transactionProcessingService.createManualTransaction(
                userId,
                request.amount(),
                request.transactionType(),
                request.category(),
                request.counterparty(),
                request.paymentMode(),
                request.transactionTime());

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable UUID id,
            @RequestBody UpdateTransactionRequest request) {

        UUID userId = SecurityUtils.currentUserId();

        Transaction updated = transactionProcessingService.updateTransaction(
                userId,
                id,
                request.counterparty(),
                request.paymentMode(),
                request.category());

        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id){
        transactionProcessingService.deleteTransaction(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .paymentMode(transaction.getPaymentMode())
                .category(transaction.getCategory())
                .counterparty(transaction.getCounterparty())
                .referenceNumber(transaction.getReferenceNumber())
                .transactionTime(transaction.getTransactionTime())
                .build();
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

    /**
     * Partial update for an existing transaction. Any field left null/blank
     * is left unchanged.
     */
    public record UpdateTransactionRequest(
            String counterparty,   // merchant, nullable
            String paymentMode,    // nullable
            String category        // nullable
    ) {
    }
}
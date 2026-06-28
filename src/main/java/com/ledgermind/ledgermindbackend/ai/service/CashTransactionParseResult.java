package com.ledgermind.ledgermindbackend.ai.service;

import java.math.BigDecimal;

/**
 * Structured result from LLM parsing of a natural language cash transaction description.
 * All fields use String for LLM output — validated and converted in CashTransactionParser.
 */
public record CashTransactionParseResult(
        BigDecimal amount,
        String transactionType,   // DEBIT or CREDIT
        String category,          // one of Category enum values
        String counterparty       // merchant/person name, nullable
) {}

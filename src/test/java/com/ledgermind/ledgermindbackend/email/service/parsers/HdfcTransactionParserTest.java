package com.ledgermind.ledgermindbackend.email.service.parsers;

import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.PaymentMode;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HdfcTransactionParserTest {

    private final HdfcTransactionParser parser = new HdfcTransactionParser();

    private RawEmail email(String body) {
        return RawEmail.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .sender("HDFC Bank InstaAlerts <alerts@hdfcbank.bank.in>")
                .body(body)
                .receivedAt(LocalDateTime.of(2026, 7, 5, 10, 0)) // stored as UTC
                .build();
    }

    @Test
    void supportsHdfcAlertsSender() {
        assertTrue(parser.supports(email("anything")));

        RawEmail other = email("anything");
        other.setSender("alerts@icicibank.com");
        assertFalse(parser.supports(other));
    }

    @Test
    void parsesUpiDebit() {
        Transaction t = parser.parse(email("""
                Dear Customer,
                Rs. 450.00 has been debited from account **1234 to VPA swiggy@icici (Swiggy Limited).
                UPI Reference No.: 123456789012
                """));

        assertEquals(new BigDecimal("450.00"), t.getAmount());
        assertEquals(TransactionType.DEBIT, t.getTransactionType());
        assertEquals(PaymentMode.UPI, t.getPaymentMode());
        assertEquals("123456789012", t.getReferenceNumber());
        assertEquals("Swiggy Limited", t.getCounterparty());
    }

    @Test
    void parsesUpiCreditWithSender() {
        Transaction t = parser.parse(email("""
                Dear Customer,
                Rs. 1,500.00 is credited to your account via UPI.
                Sender: Rahul Kumar (rahul@upi)
                UPI Reference No.: 987654321098
                """));

        assertEquals(new BigDecimal("1500.00"), t.getAmount());
        assertEquals(TransactionType.CREDIT, t.getTransactionType());
        assertEquals(PaymentMode.UPI, t.getPaymentMode());
        assertEquals("Rahul Kumar", t.getCounterparty());
    }

    @Test
    void parsesCreditCardSpendWithEmbeddedTransactionTime() {
        Transaction t = parser.parse(email("""
                Dear Customer,
                Rs. 2,499.00 was spent on your HDFC Bank Credit Card ending 4321
                towards AMAZON PAY INDIA on 05 Jul, 2026 at 14:30:22.
                """));

        assertEquals(new BigDecimal("2499.00"), t.getAmount());
        assertEquals(TransactionType.DEBIT, t.getTransactionType());
        assertEquals(PaymentMode.CREDIT_CARD, t.getPaymentMode());
        assertEquals("AMAZON PAY INDIA", t.getCounterparty());
        assertEquals(LocalDateTime.of(2026, 7, 5, 14, 30, 22), t.getTransactionTime());
    }

    @Test
    void fallsBackToReceivedAtConvertedToIst() {
        Transaction t = parser.parse(email("Rs. 100.00 has been debited via UPI."));

        // receivedAt is stored as UTC; 10:00 UTC == 15:30 IST
        assertEquals(LocalDateTime.of(2026, 7, 5, 15, 30), t.getTransactionTime());
    }

    @Test
    void unparseableBodyDegradesGracefully() {
        Transaction t = parser.parse(email("Welcome to HDFC Bank NetBanking."));

        assertEquals(BigDecimal.ZERO, t.getAmount());
        assertEquals(TransactionType.UNKNOWN, t.getTransactionType());
        assertEquals(PaymentMode.UNKNOWN, t.getPaymentMode());
        assertNull(t.getCounterparty());
        assertNull(t.getReferenceNumber());
    }
}

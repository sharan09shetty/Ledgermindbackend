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
    void parsesOldTemplateUpiDebitWithVpaHolderName() {
        Transaction t = parser.parse(email("""
                HDFC BANK Dear Customer, Rs.50.00 has been debited from account 7748 to VPA \
                paytm.s23k47m@pty SUHAIBULLA MAHBOOB PASHA on 07-05-26. Your UPI transaction \
                reference number is 649320323376. If you did not authorize this transaction, \
                please report it immediately by calling 18002586161 Or SMS BLOCK UPI to 7308080808. \
                Warm Regards, HDFC Bank"""));

        assertEquals(new BigDecimal("50.00"), t.getAmount());
        assertEquals(TransactionType.DEBIT, t.getTransactionType());
        assertEquals(PaymentMode.UPI, t.getPaymentMode());
        assertEquals("SUHAIBULLA MAHBOOB PASHA", t.getCounterparty());
        assertEquals("649320323376", t.getReferenceNumber());
    }

    @Test
    void parsesOldTemplateUpiCreditWithVpaHolderName() {
        Transaction t = parser.parse(email("""
                HDFC BANK Dear Customer, Rs. 9550.00 is successfully credited to your account \
                **7748 by VPA 8904145188@yescred RACHITH J SHETTY on 06-05-26. Your UPI \
                transaction reference number is 649224286968. Thank you for banking with us. \
                Warm Regards, HDFC Bank"""));

        assertEquals(new BigDecimal("9550.00"), t.getAmount());
        assertEquals(TransactionType.CREDIT, t.getTransactionType());
        assertEquals(PaymentMode.UPI, t.getPaymentMode());
        assertEquals("RACHITH J SHETTY", t.getCounterparty());
        assertEquals("649224286968", t.getReferenceNumber());
    }

    @Test
    void parsesCreditCardMerchantWithStarDescriptor() {
        Transaction t = parser.parse(email("""
                HDFC BANK Dear Customer, Greetings from HDFC Bank. We would like to inform you \
                that Rs. 2399.00 has been debited from your HDFC Bank Credit Card ending 5555 \
                towards ANTHROPIC* CLAUDE SUB on 10 Jul, 2026 at 23:29:08. Need Help? Call us at \
                1800 1600 or 1800 2600 (Toll-free, across India) Thank you for banking with us."""));

        assertEquals(new BigDecimal("2399.00"), t.getAmount());
        assertEquals(TransactionType.DEBIT, t.getTransactionType());
        assertEquals(PaymentMode.CREDIT_CARD, t.getPaymentMode());
        assertEquals("ANTHROPIC* CLAUDE SUB", t.getCounterparty());
        assertEquals(LocalDateTime.of(2026, 7, 10, 23, 29, 8), t.getTransactionTime());
    }

    @Test
    void parsesCreditCardMerchantMixedCaseDescriptors() {
        Transaction razorpay = parser.parse(email("""
                Rs. 721.65 has been debited from your HDFC Bank Credit Card ending 5555 \
                towards RAZ*Makemytrip India P on 16 Jun, 2026 at 19:18:39. (Toll-free, across India)"""));
        assertEquals("RAZ*Makemytrip India P", razorpay.getCounterparty());

        Transaction rezoni = parser.parse(email("""
                Rs. 399.00 has been debited from your HDFC Bank Credit Card ending 5555 \
                towards Ing*rezoni on 14 Jun, 2026 at 23:12:57. (Toll-free, across India)"""));
        assertEquals("Ing*rezoni", rezoni.getCounterparty());
    }

    @Test
    void parsesOldCreditCardTemplate() {
        Transaction t = parser.parse(email("""
                HDFC BANK Dear Customer, Greetings from HDFC Bank! Rs.796.40 is debited from your \
                HDFC Bank Credit Card ending 5555 towards CAS*REDBUS INDIA PRIVA on 12 Feb, 2026 \
                at 18:41:24. If you did not authorize this transaction, please report it \
                immediately at: - When in India (Toll free): 1800 258 6161"""));

        assertEquals(new BigDecimal("796.40"), t.getAmount());
        assertEquals("CAS*REDBUS INDIA PRIVA", t.getCounterparty());
        assertEquals(LocalDateTime.of(2026, 2, 12, 18, 41, 24), t.getTransactionTime());
    }

    @Test
    void parsesChequeDepositCounterpartyWithoutSwallowingSentence() {
        Transaction t = parser.parse(email("""
                HDFC BANK Dear Customer, Greetings from HDFC Bank. Cheque 1655853136 of \
                INR 1,04,013.00 has been successfully deposited in your A/c ending XX7748 from \
                M G ROAD on 27-MAY-2026. The available balance in your account is INR 1,15,048.13."""));

        assertEquals(new BigDecimal("104013.00"), t.getAmount());
        assertEquals(TransactionType.CREDIT, t.getTransactionType());
        assertEquals(PaymentMode.CHEQUE, t.getPaymentMode());
        assertEquals("M G ROAD", t.getCounterparty());
    }

    @Test
    void skipsUpcomingEmandateNotice() {
        Transaction t = parser.parse(email("""
                HDFC BANK Dear Customer, Greetings from HDFC Bank! There is an upcoming E-mandate \
                (Auto payment) of INR 199.00 for NETFLIX. Amount will be debited from your HDFC \
                Bank Credit Card ending 5555 on 02/02/2026. SI Hub ID: Xy54MrA7kH"""));

        assertNull(t);
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

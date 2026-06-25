package com.ledgermind.ledgermindbackend.email.service.parsers;

import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.PaymentMode;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import com.ledgermind.ledgermindbackend.email.service.TransactionParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class HdfcTransactionParser implements TransactionParser {

    private String HDFC_EMAIL_ID = "alerts@hdfcbank.bank.in";

    @Override
    public boolean supports(RawEmail email) {
        return email.getSender().contains(HDFC_EMAIL_ID);
    }

    @Override
    public Transaction parse(RawEmail email) {
        String body = email.getBody();
        return Transaction.builder()
                .userId(email.getUserId())
                .rawEmailId(email.getId())
                .amount(extractAmount(email.getBody()))
                .transactionType(determineTransactionType(body))
                .paymentMode(determinePaymentMode(body))
                .referenceNumber(extractReference(body))
                .counterparty(extractCounterparty(body))
                .transactionTime(extractTransactionTime(body, email.getReceivedAt()))
                .created(LocalDateTime.now())
                .build();
    }

    private static final Pattern RS_AMOUNT_PATTERN =
            Pattern.compile("Rs\\.\\s*([\\d,]+(?:\\.\\d{2})?)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern INR_AMOUNT_PATTERN =
            Pattern.compile("INR\\s*([\\d,]+(?:\\.\\d{2})?)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern UPI_REFERENCE_PATTERN =
            Pattern.compile(
                    "UPI\\s+Reference\\s+No\\.:?\\s*(\\d+)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern SENDER_PATTERN =
            Pattern.compile(
                    "Sender:\\s*([^\\(\\n]+)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern VPA_MERCHANT_PATTERN =
            Pattern.compile(
                    "\\(([^\\)]+)\\)"
            );

    private static final Pattern CHEQUE_DEPOSIT_PATTERN =
            Pattern.compile(
                    "from\\s+(.+?)\\s+on\\s+\\d{2}-[A-Z]{3}-\\d{4}",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CC_MERCHANT_PATTERN =
            Pattern.compile(
                    "towards\\s+([A-Z0-9 _&'./-]+?)\\s+on\\s+\\d{2}\\s+[A-Za-z]{3}",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CC_TRANSACTION_TIME_PATTERN =
            Pattern.compile(
                    "on\\s+(\\d{1,2}\\s+[A-Za-z]{3},?\\s+\\d{4})\\s+at\\s+(\\d{2}:\\d{2}:\\d{2})",
                    Pattern.CASE_INSENSITIVE
            );

    private static final DateTimeFormatter CC_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("d MMM[,] yyyy HH:mm:ss", Locale.ENGLISH);

    private TransactionType determineTransactionType(String body) {

        String content = body.toLowerCase();

        if (content.contains("credited")
                || content.contains("credit of")
                || content.contains("deposited")
                || content.contains("received")) {
            return TransactionType.CREDIT;
        }

        if (content.contains("debited")
                || content.contains("spent")
                || content.contains("withdrawn")
                || content.contains("purchase")
                || content.contains("paid")
                || content.contains("sent")) {
            return TransactionType.DEBIT;
        }
        log.error("Unable to determine transaction type for email body: {}", body);
        return TransactionType.UNKNOWN;
    }

    private PaymentMode determinePaymentMode(String body) {

        String content = body.toLowerCase();

        if (content.contains("upi")) {
            return PaymentMode.UPI;
        }

        if (content.contains("cheque")) {
            return PaymentMode.CHEQUE;
        }

        if (content.contains("credit card")) {
            return PaymentMode.CREDIT_CARD;
        }

        if (content.contains("debit card")) {
            return PaymentMode.DEBIT_CARD;
        }

        log.error("Unable to determine payment mode for email body: {}", body);
        return PaymentMode.UNKNOWN;
    }

    private BigDecimal extractAmount(String body) {

        Matcher matcher = RS_AMOUNT_PATTERN.matcher(body);

        if (matcher.find()) {

            return new BigDecimal(
                    matcher.group(1)
                            .replace(",", "")
            );
        }

        matcher = INR_AMOUNT_PATTERN.matcher(body);

        if (matcher.find()) {

            return new BigDecimal(
                    matcher.group(1)
                            .replace(",", "")
            );
        }

        log.error("Unable to extract amount from email body: {}", body);
        return BigDecimal.ZERO;
    }

    private String extractReference(String body) {

        Matcher matcher =
                UPI_REFERENCE_PATTERN.matcher(body);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String extractCounterparty(String body) {

        Matcher ccMatcher = CC_MERCHANT_PATTERN.matcher(body);
        if (ccMatcher.find()) {
            return ccMatcher.group(1).trim();
        }

        Matcher senderMatcher =
                SENDER_PATTERN.matcher(body);

        if (senderMatcher.find()) {
            return senderMatcher.group(1).trim();
        }

        Matcher merchantMatcher =
                VPA_MERCHANT_PATTERN.matcher(body);

        if (merchantMatcher.find()) {
            return merchantMatcher.group(1).trim();
        }

        Matcher chequeMatcher =
                CHEQUE_DEPOSIT_PATTERN.matcher(body);

        if (chequeMatcher.find()) {
            return chequeMatcher.group(1).trim();
        }

        return null;
    }

    private LocalDateTime extractTransactionTime(String body, LocalDateTime fallback) {
        Matcher matcher = CC_TRANSACTION_TIME_PATTERN.matcher(body);
        if (matcher.find()) {
            try {
                String datePart = matcher.group(1).replace(",", "");
                String timePart = matcher.group(2);
                return LocalDateTime.parse(datePart + " " + timePart, CC_DATE_TIME_FORMATTER);
            } catch (Exception e) {
                log.warn("Failed to parse transaction time from body, falling back to receivedAt: {}", e.getMessage());
            }
        }
        return fallback;
    }
}
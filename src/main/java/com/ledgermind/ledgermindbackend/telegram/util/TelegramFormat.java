package com.ledgermind.ledgermindbackend.telegram.util;

import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.email.enums.PaymentMode;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared formatting helpers for building Telegram messages (HTML parse mode).
 * Pure/stateless — safe to call from any thread.
 */
public final class TelegramFormat {

    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_AND_TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_AND_TIME_WITH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private TelegramFormat() {}

    public static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        // NumberFormat isn't thread-safe — create a fresh instance per call.
        NumberFormat inrFormat = NumberFormat.getInstance(new Locale("en", "IN"));
        inrFormat.setMinimumFractionDigits(2);
        inrFormat.setMaximumFractionDigits(2);
        return inrFormat.format(amount);
    }

    public static String formatTime(LocalDateTime time) {
        LocalDate date = time.toLocalDate();
        LocalDate today = LocalDate.now();

        if (date.isEqual(today)) {
            return "Today, " + time.format(TIME_ONLY);
        }
        if (date.isEqual(today.minusDays(1))) {
            return "Yesterday, " + time.format(TIME_ONLY);
        }
        if (date.getYear() == today.getYear()) {
            return time.format(DATE_AND_TIME);
        }
        return time.format(DATE_AND_TIME_WITH_YEAR);
    }

    public static String formatDayLabel(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isEqual(today)) return "Today";
        if (date.isEqual(today.minusDays(1))) return "Yesterday";
        return date.format(DAY_LABEL);
    }

    public static String typeEmoji(TransactionType type) {
        if (type == null) return "⚪";
        return switch (type) {
            case DEBIT -> "🔴";
            case CREDIT -> "🟢";
            case UNKNOWN -> "⚪";
        };
    }

    public static String typeVerb(TransactionType type) {
        if (type == null) return "recorded";
        return switch (type) {
            case DEBIT -> "spent";
            case CREDIT -> "received";
            case UNKNOWN -> "recorded";
        };
    }

    public static String categoryEmoji(Category category) {
        if (category == null) return "📦";
        return switch (category) {
            case FOOD -> "🍔";
            case TRAVEL -> "✈️";
            case ENTERTAINMENT -> "🎬";
            case SHOPPING -> "🛍️";
            case BILLS -> "🧾";
            case INVESTMENT -> "📈";
            case SALARY -> "💼";
            case TRANSFER -> "🔁";
            case HEALTH -> "🏥";
            case OTHER -> "📦";
        };
    }

    public static String paymentEmoji(PaymentMode mode) {
        if (mode == null) return "❔";
        return switch (mode) {
            case UPI -> "📱";
            case CREDIT_CARD, DEBIT_CARD -> "💳";
            case CASH -> "💵";
            case CHEQUE -> "🧾";
            case NEFT, IMPS, RTGS -> "🏦";
            case UNKNOWN -> "❔";
        };
    }

    public static String label(Enum<?> value) {
        if (value == null) return "Uncategorized";
        String[] words = value.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return sb.toString();
    }

    public static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

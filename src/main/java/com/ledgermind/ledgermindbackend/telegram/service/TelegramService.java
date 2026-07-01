package com.ledgermind.ledgermindbackend.telegram.service;

import com.ledgermind.ledgermindbackend.analytics.dto.DailyInsightResponse;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import com.ledgermind.ledgermindbackend.telegram.client.TelegramClient;
import com.ledgermind.ledgermindbackend.telegram.config.TelegramProperties;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramChatActionRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramSendMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.ledgermind.ledgermindbackend.telegram.util.TelegramFormat.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;

    public Long sendMessage(TelegramMessageRequest request) {
        TelegramSendMessageResponse response = telegramClient.sendMessage(telegramProperties.getBotToken(), request);
        log.info("Telegram message sent to chatId={}", request.chat_id());
        return response.getResult().getMessageId();
    }

    /**
     * Shows a "typing..." indicator in Telegram while Gemini is processing.
     * Fire-and-forget — failure is non-fatal.
     */
    public void sendChatAction(Long chatId, String action) {
        try {
            telegramClient.sendChatAction(
                    telegramProperties.getBotToken(),
                    TelegramChatActionRequest.builder()
                            .chat_id(chatId.toString())
                            .action(action)
                            .build());
        } catch (Exception e) {
            log.warn("Failed to send chat action '{}' to chatId={}: {}", action, chatId, e.getMessage());
        }
    }

    // ── Per-transaction notification ────────────────────────────────────────

    public Long sendTransactionNotification(String chatId, Transaction transaction) {
        return sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId)
                .text(buildTransactionMessage(transaction))
                .parse_mode("HTML")
                .build()
        );
    }

    private String buildTransactionMessage(Transaction transaction) {
        TransactionType type = transaction.getTransactionType();
        String amount = formatAmount(transaction.getAmount());

        StringBuilder sb = new StringBuilder();

        // ── Headline: "🔴 ₹46,343.00 spent at CRED Club" ──
        sb.append(typeEmoji(type)).append(" <b>₹").append(amount).append("</b> ").append(typeVerb(type));
        if (transaction.getCounterparty() != null && !transaction.getCounterparty().isBlank()) {
            sb.append(" at <b>").append(escapeHtml(transaction.getCounterparty())).append("</b>");
        }
        sb.append("\n");

        // ── Category · Payment mode ──
        sb.append(categoryEmoji(transaction.getCategory())).append(" ").append(label(transaction.getCategory()));
        sb.append("  ·  ");
        sb.append(paymentEmoji(transaction.getPaymentMode())).append(" ").append(label(transaction.getPaymentMode()));
        sb.append("\n");

        // ── Time ──
        if (transaction.getTransactionTime() != null) {
            sb.append("🕒 ").append(formatTime(transaction.getTransactionTime())).append("\n");
        }

        // ── Reference number, only if present ──
        if (transaction.getReferenceNumber() != null && !transaction.getReferenceNumber().isBlank()) {
            sb.append("🔗 Ref: <code>").append(escapeHtml(transaction.getReferenceNumber())).append("</code>\n");
        }

        sb.append("\n<i>Reply to this message to correct the category</i>");

        return sb.toString();
    }

    // ── Nightly daily-insight summary ───────────────────────────────────────

    public Long sendDailyInsight(String chatId, DailyInsightResponse insight) {
        return sendMessage(TelegramMessageRequest.builder()
                .chat_id(chatId)
                .text(buildDailyInsightMessage(insight))
                .parse_mode("HTML")
                .build()
        );
    }

    private String buildDailyInsightMessage(DailyInsightResponse insight) {
        StringBuilder sb = new StringBuilder();

        sb.append("🌙 <b>").append(formatDayLabel(insight.getDate())).append("'s Summary</b>\n\n");

        boolean hasSpend = insight.getTotalSpent() != null && insight.getTotalSpent().compareTo(BigDecimal.ZERO) > 0;
        boolean hasReceived = insight.getTotalReceived() != null && insight.getTotalReceived().compareTo(BigDecimal.ZERO) > 0;

        if (hasSpend) {
            sb.append("🔴 Spent: <b>₹").append(formatAmount(insight.getTotalSpent())).append("</b>\n");
        } else {
            sb.append("🎉 No spending today!\n");
        }

        if (hasReceived) {
            sb.append("🟢 Received: ₹").append(formatAmount(insight.getTotalReceived())).append("\n");
        }

        sb.append("📋 ").append(insight.getTransactionCount()).append(" transaction")
                .append(insight.getTransactionCount() == 1 ? "" : "s").append(" today\n");

        if (hasSpend && insight.getTopCategory() != null) {
            sb.append("\n").append(categoryEmoji(insight.getTopCategory())).append(" Top category: ")
                    .append(label(insight.getTopCategory()));
            if (insight.getTopCategorySpend() != null) {
                sb.append(" — ₹").append(formatAmount(insight.getTopCategorySpend()));
            }
            sb.append("\n");
        }

        if (hasSpend && insight.getTopMerchant() != null) {
            sb.append("🏪 Top merchant: ").append(escapeHtml(insight.getTopMerchant()));
            if (insight.getTopMerchantSpend() != null) {
                sb.append(" — ₹").append(formatAmount(insight.getTopMerchantSpend()));
            }
            sb.append("\n");
        }

        if (insight.getHighestTransactionAmount() != null) {
            sb.append("\n🏆 Highest expense: <b>₹").append(formatAmount(insight.getHighestTransactionAmount())).append("</b>");
            if (insight.getHighestTransactionMerchant() != null) {
                sb.append(" at ").append(escapeHtml(insight.getHighestTransactionMerchant()));
            }
            if (insight.getHighestTransactionCategory() != null) {
                sb.append(" (").append(categoryEmoji(insight.getHighestTransactionCategory()))
                        .append(" ").append(label(insight.getHighestTransactionCategory())).append(")");
            }
        }

        return sb.toString();
    }
}

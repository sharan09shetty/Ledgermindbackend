package com.ledgermind.ledgermindbackend.telegram.service;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.telegram.client.TelegramClient;
import com.ledgermind.ledgermindbackend.telegram.config.TelegramProperties;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramSendMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public Long sendTransactionNotification(String chatId, Transaction transaction) {
        return sendMessage(TelegramMessageRequest.builder()
                        .chat_id(chatId)
                        .text(buildTransactionMessage(transaction))
                        .build()
        );
    }

    private String buildTransactionMessage(Transaction transaction) {
        return """
            💰 New Transaction

            Amount: ₹%s
            Type: %s
            Payment Mode: %s
            Counterparty: %s
            Category: %s
            Time: %s
            """
                .formatted(
                        transaction.getAmount(),
                        transaction.getTransactionType(),
                        transaction.getPaymentMode(),
                        transaction.getCounterparty(),
                        transaction.getCategory(),
                        transaction.getTransactionTime()
                );
    }
}
package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.email.service.TransactionProcessingService;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramUpdate;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {
    private final TelegramService telegramService;
    private final TransactionProcessingService transactionProcessingService;
    private final String TELEGRAM_CHAT_ID = "1335526793";

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(@RequestBody TelegramUpdate update) {
        Long chatId = update.getMessage().getChat().getId();
        String keyword = update.getMessage().getText();
        Long timestamp = update.getMessage().getDate();
        Long messageId = update.getMessage().getMessageId();
        log.info("chatId={}, keyword={}, timestamp={}, messageId={}", chatId, keyword, timestamp, messageId);
        if(update.getMessage().getReplyToMessage() !=null){
            Long repliedMessageId = update.getMessage().getReplyToMessage().getMessageId();
            log.info("Received reply to messageId={}", repliedMessageId);
            transactionProcessingService.updateTransactionCategory(chatId, repliedMessageId, keyword);
        }else{
            sendReplyMessage(chatId, "Please reply to a transaction message with the category you want to set. For example, if you want to categorize a transaction as 'Food', reply to the transaction message with 'Food'.");
        }
        return ResponseEntity.ok().build();
    }

    public void sendReplyMessage(Long chatId, String text){
        TelegramMessageRequest reply = TelegramMessageRequest.builder()
                .chat_id(chatId.toString())
                .text(text)
                .build();
        telegramService.sendMessage(reply);
    }
}

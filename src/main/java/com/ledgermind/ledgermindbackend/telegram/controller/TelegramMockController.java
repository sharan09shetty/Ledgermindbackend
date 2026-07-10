package com.ledgermind.ledgermindbackend.telegram.controller;

import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageResult;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramSendMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Stand-in for the Telegram Bot API in local dev (telegram.api-url points here).
@RestController
@Profile("local")
@Slf4j
@RequestMapping("/mock-telegram-api")
public class TelegramMockController {

    @PostMapping("/bot{token}/sendMessage")
    public TelegramSendMessageResponse sendMessage(@RequestBody TelegramMessageRequest request) {
        log.info("Mock sendMessage called with chat_id={}, text={}", request.chat_id(), request.text());
        return buildTelegramSendMessageResponse(request);
    }

    private TelegramSendMessageResponse buildTelegramSendMessageResponse(TelegramMessageRequest request) {
        return TelegramSendMessageResponse.builder()
                .ok(true)
                .result(TelegramMessageResult.builder()
                        .messageId(1233345L)
                        .build())
                .build();
    }
}

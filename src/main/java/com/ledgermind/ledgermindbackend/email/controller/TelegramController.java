package com.ledgermind.ledgermindbackend.email.controller;

import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TelegramController {
    private final TelegramService telegramService;

    @PostMapping("/message/test")
    public void testTelegramMessage(@RequestBody TelegramMessageRequest request) throws Exception {
        telegramService.sendMessage(request);
    }
}

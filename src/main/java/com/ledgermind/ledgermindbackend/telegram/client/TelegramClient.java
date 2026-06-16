package com.ledgermind.ledgermindbackend.telegram.client;

import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramSendMessageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "telegram-client",
        url = "${telegram.api-url}"
)
public interface TelegramClient {

    @PostMapping("/bot{token}/sendMessage")
    TelegramSendMessageResponse sendMessage(
            @PathVariable("token") String token,
            @RequestBody TelegramMessageRequest request
    );
}
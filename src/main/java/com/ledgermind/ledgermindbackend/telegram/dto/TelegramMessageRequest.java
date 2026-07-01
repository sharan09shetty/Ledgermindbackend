package com.ledgermind.ledgermindbackend.telegram.dto;

import lombok.Builder;

@Builder
public record TelegramMessageRequest(
        String chat_id,
        String text,
        String parse_mode
) {
}
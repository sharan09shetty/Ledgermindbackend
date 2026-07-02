package com.ledgermind.ledgermindbackend.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record TelegramMessageRequest(
        String chat_id,
        String text,
        String parse_mode
) {
}
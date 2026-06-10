package com.ledgermind.ledgermindbackend.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramMessageResult {
    @JsonProperty("message_id")
    private Long messageId;
}

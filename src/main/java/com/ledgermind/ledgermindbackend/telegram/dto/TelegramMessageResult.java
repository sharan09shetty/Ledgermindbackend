package com.ledgermind.ledgermindbackend.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TelegramMessageResult {
    @JsonProperty("message_id")
    private Long messageId;
}

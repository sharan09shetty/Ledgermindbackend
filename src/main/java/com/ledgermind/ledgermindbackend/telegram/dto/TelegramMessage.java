package com.ledgermind.ledgermindbackend.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TelegramMessage {

    @JsonProperty("message_id")
    private Long messageId;

    private String text;

    private Long date;

    private TelegramChat chat;

    @JsonProperty("reply_to_message")
    private TelegramMessage replyToMessage;
}

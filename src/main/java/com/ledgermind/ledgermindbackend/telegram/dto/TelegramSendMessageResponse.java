package com.ledgermind.ledgermindbackend.telegram.dto;

import lombok.Data;

@Data
public class TelegramSendMessageResponse {

    private Boolean ok;

    private TelegramMessageResult result;
}

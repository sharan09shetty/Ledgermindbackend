package com.ledgermind.ledgermindbackend.telegram.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TelegramSendMessageResponse {

    private Boolean ok;

    private TelegramMessageResult result;
}

package com.ledgermind.ledgermindbackend.telegram.dto;

import lombok.Data;

@Data
public class TelegramUpdate {

    private Long updateId;

    private TelegramMessage message;
}

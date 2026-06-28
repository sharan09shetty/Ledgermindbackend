package com.ledgermind.ledgermindbackend.telegram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramChatActionRequest {
    private String chat_id;
    private String action;
}

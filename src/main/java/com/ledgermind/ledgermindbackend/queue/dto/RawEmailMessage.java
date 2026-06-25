package com.ledgermind.ledgermindbackend.queue.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RawEmailMessage(
        UUID rawEmailId,
        UUID userId,
        String sender,
        String body,
        LocalDateTime receivedAt,
        String telegramChatId
) {}
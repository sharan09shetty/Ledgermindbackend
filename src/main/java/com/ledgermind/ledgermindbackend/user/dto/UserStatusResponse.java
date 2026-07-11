package com.ledgermind.ledgermindbackend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusResponse {
    private String email;
    private String name;
    private String bankCode;
    private String bankName;
    private boolean gmailConnected;
    private boolean telegramLinked;
    private boolean active;
    private boolean onboarded;
}
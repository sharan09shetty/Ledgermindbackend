package com.ledgermind.ledgermindbackend.user.controller;

import com.ledgermind.ledgermindbackend.email.entity.Bank;
import com.ledgermind.ledgermindbackend.email.repository.BankRepository;
import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramLinkService;
import com.ledgermind.ledgermindbackend.user.dto.UserStatusResponse;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import com.ledgermind.ledgermindbackend.user.service.UserScanSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserScanSchedulerService userScanSchedulerService;
    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final TelegramLinkService telegramLinkService;


    @PatchMapping("/bank")
    public ResponseEntity<UserStatusResponse> setBank(@RequestParam String code) {
        UUID userId = SecurityUtils.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Bank bank = bankRepository.findById(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown bank code: " + code));

        user.setBank(bank);

        if (user.isReadyForScanning()) {
            user.setActive(true);
        }

        userRepository.save(user);

        boolean telegramLinked = telegramLinkService.isLinked(userId);
        return ResponseEntity.ok(toStatusResponse(user, telegramLinked));
    }

    @GetMapping("/status")
    public ResponseEntity<UserStatusResponse> getStatus() {
        UUID userId = SecurityUtils.currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean telegramLinked = telegramLinkService.isLinked(userId);
        return ResponseEntity.ok(toStatusResponse(user, telegramLinked));
    }

    // Internal dev trigger — consider removing or restricting in production
    @PostMapping("/mock/test")
    public void triggerScan() {
        userScanSchedulerService.triggerScans();
    }

    private UserStatusResponse toStatusResponse(User user, boolean telegramLinked) {
        return UserStatusResponse.builder()
                .email(user.getEmail())
                .name(user.getName())
                .bankCode(user.getBank() != null ? user.getBank().getCode() : null)
                .bankName(user.getBank() != null ? user.getBank().getName() : null)
                .gmailConnected(user.isGmailConnected())
                .telegramLinked(telegramLinked)
                .active(Boolean.TRUE.equals(user.getActive()))
                .build();
    }
}
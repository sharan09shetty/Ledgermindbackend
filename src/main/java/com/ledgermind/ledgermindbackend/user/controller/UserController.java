package com.ledgermind.ledgermindbackend.user.controller;

import com.ledgermind.ledgermindbackend.email.entity.Bank;
import com.ledgermind.ledgermindbackend.email.repository.BankRepository;
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

    @PostMapping("/mock/test")
    public void testEmails() {
        userScanSchedulerService.triggerScans();
    }

    @PatchMapping("/{id}/bank")
    public ResponseEntity<User> setBank(@PathVariable UUID id, @RequestParam String code) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        Bank bank = bankRepository.findById(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown bank code: " + code));

        user.setBank(bank);

        if (user.getTelegramChatId() != null) {
            user.setActive(true);
        }

        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<UserStatusResponse> getStatus(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        return ResponseEntity.ok(UserStatusResponse.builder()
                .email(user.getEmail())
                .bankCode(user.getBank() != null ? user.getBank().getCode() : null)
                .bankName(user.getBank() != null ? user.getBank().getName() : null)
                .telegramLinked(user.getTelegramChatId() != null)
                .active(Boolean.TRUE.equals(user.getActive()))
                .build());
    }
}
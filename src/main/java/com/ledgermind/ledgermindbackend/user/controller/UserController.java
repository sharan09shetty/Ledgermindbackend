package com.ledgermind.ledgermindbackend.user.controller;

import com.ledgermind.ledgermindbackend.user.service.UserScanSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserScanSchedulerService userService;

    @PostMapping("/mock/test")
    public void testEmails() throws Exception {
        userService.triggerScans();
    }
}

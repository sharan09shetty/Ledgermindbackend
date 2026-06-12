package com.ledgermind.ledgermindbackend.user.service;

import com.ledgermind.ledgermindbackend.email.dto.Bank;
import com.ledgermind.ledgermindbackend.email.service.GmailService;
import com.ledgermind.ledgermindbackend.email.dto.GmailScanRequest;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserScanSchedulerService {

    private final UserRepository userRepository;
    private  final GmailService gmailService;

    public void triggerScans() {
        List<User> users = userRepository.findByActiveTrue();
        log.info("Found {} active users", users.size());
    }
}
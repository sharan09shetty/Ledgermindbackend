package com.ledgermind.ledgermindbackend.email.controller;

import com.ledgermind.ledgermindbackend.email.dto.BankResponse;
import com.ledgermind.ledgermindbackend.email.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BankController {

    private final BankRepository bankRepository;

    @GetMapping("/banks")
    public List<BankResponse> getBanks() {
        return bankRepository.findAll().stream()
                .filter(bank -> Boolean.TRUE.equals(bank.getActive()))
                .map(bank -> BankResponse.builder()
                        .code(bank.getCode())
                        .name(bank.getName())
                        .build())
                .toList();
    }
}
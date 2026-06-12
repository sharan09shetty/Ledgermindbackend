package com.ledgermind.ledgermindbackend.email.repository;

import com.ledgermind.ledgermindbackend.email.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BankRepository extends JpaRepository<Bank, UUID> {
}

package com.ledgermind.ledgermindbackend.email.repository;

import com.ledgermind.ledgermindbackend.email.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, String> {
}
package com.ledgermind.ledgermindbackend.email.repository;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Transaction findByTelegramMessageIdAndUserId(Long telegramMessageId, UUID userId);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByRawEmailId(UUID rawEmailId);

    void deleteById(UUID id);
}
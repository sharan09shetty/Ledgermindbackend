package com.ledgermind.ledgermindbackend.telegram.repository;

import com.ledgermind.ledgermindbackend.telegram.entity.TelegramLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, UUID> {

    Optional<TelegramLink> findByUserId(UUID userId);

    Optional<TelegramLink> findByChatId(String chatId);

    /**
     * Row-locking lookup used only when claiming a token. Holding a
     * PESSIMISTIC_WRITE lock for the duration of the claim transaction means
     * a second concurrent attempt with the same token (replay, double-tap,
     * etc.) blocks until the first commits - at which point link_token is
     * already null, so the second attempt's WHERE clause simply finds no
     * matching row and is correctly rejected as invalid. This is what makes
     * the token single-use under concurrency, not just in the happy path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TelegramLink t WHERE t.linkToken = :token")
    Optional<TelegramLink> findByLinkTokenForUpdate(@Param("token") String token);
}

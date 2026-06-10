package com.ledgermind.ledgermindbackend.email.repository;

import com.ledgermind.ledgermindbackend.email.entity.RawEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RawEmailRepository extends JpaRepository<RawEmail, UUID> {

    boolean existsByGmailMessageId(String gmailMessageId);

    List<RawEmail> findTop10ByProcessedFalseOrderByReceivedAtAsc();
}

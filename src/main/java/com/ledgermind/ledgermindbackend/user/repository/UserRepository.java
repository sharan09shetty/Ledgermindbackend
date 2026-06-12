package com.ledgermind.ledgermindbackend.user.repository;

import com.ledgermind.ledgermindbackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Modifying
    @Query("""
        UPDATE User u
        SET u.lastEmailSyncTime = :lastEmailSyncTime
        WHERE u.id = :id
    """)
    void updateLastEmailSyncTimeById(@Param("id") UUID id, @Param("lastEmailSyncTime") LocalDateTime lastEmailSyncTime
    );

    Optional<User> findByEmail(String email);
    List<User> findByActiveTrue();
}

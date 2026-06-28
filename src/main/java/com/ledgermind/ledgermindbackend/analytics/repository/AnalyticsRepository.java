package com.ledgermind.ledgermindbackend.analytics.repository;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AnalyticsRepository extends JpaRepository<Transaction, UUID> {


    @Query(value = """
                SELECT COALESCE(SUM(amount), 0)
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_type = 'DEBIT'
                  AND transaction_time BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal sumDebits(@Param("userId") UUID userId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    @Query(value = """
                SELECT COALESCE(SUM(amount), 0)
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_type = 'CREDIT'
                  AND transaction_time BETWEEN :from AND :to
            """, nativeQuery = true)
    BigDecimal sumCredits(@Param("userId") UUID userId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    @Query(value = """
                SELECT COUNT(*)
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_time BETWEEN :from AND :to
            """, nativeQuery = true)
    long countTransactions(@Param("userId") UUID userId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    @Query(value = """
                SELECT category, SUM(amount)
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_type = 'DEBIT'
                  AND category IS NOT NULL
                  AND transaction_time BETWEEN :from AND :to
                GROUP BY category
                ORDER BY SUM(amount) DESC
            """, nativeQuery = true)
    List<Object[]> topCategoryRaw(@Param("userId") UUID userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query(value = """
                SELECT counterparty, SUM(amount)
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_type = 'DEBIT'
                  AND counterparty IS NOT NULL
                  AND transaction_time BETWEEN :from AND :to
                GROUP BY counterparty
                ORDER BY SUM(amount) DESC
            """, nativeQuery = true)
    List<Object[]> topMerchantRaw(@Param("userId") UUID userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    // ── Monthly breakdown ─────────────────────────────────────────────────────

    @Query(value = """
                SELECT
                    EXTRACT(YEAR FROM transaction_time) AS year,
                    EXTRACT(MONTH FROM transaction_time) AS month,
                    transaction_type,
                    SUM(amount) AS total,
                    COUNT(*) AS txn_count
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_time BETWEEN :from AND :to
                GROUP BY
                    EXTRACT(YEAR FROM transaction_time),
                    EXTRACT(MONTH FROM transaction_time),
                    transaction_type
                ORDER BY year, month
            """, nativeQuery = true)
    List<Object[]> monthlyRaw(@Param("userId") UUID userId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    // ── Category breakdown ────────────────────────────────────────────────────

    @Query(value = """
                SELECT category, SUM(amount) AS total, COUNT(*) AS txn_count
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_type = 'DEBIT'
                  AND category IS NOT NULL
                  AND transaction_time BETWEEN :from AND :to
                GROUP BY category
                ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> categoryBreakdownRaw(@Param("userId") UUID userId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    // ── Merchant breakdown ────────────────────────────────────────────────────

    @Query(value = """
                SELECT
                    t.counterparty,
                    SUM(t.amount) AS total_spend,
                    COUNT(*) AS txn_count,
                    (
                        SELECT t2.category
                        FROM transactions t2
                        WHERE t2.user_id = :userId
                          AND t2.counterparty = t.counterparty
                          AND t2.transaction_type = 'DEBIT'
                          AND t2.transaction_time BETWEEN :from AND :to
                          AND t2.category IS NOT NULL
                        GROUP BY t2.category
                        ORDER BY COUNT(*) DESC
                        LIMIT 1
                    ) AS top_category
                FROM transactions t
                WHERE t.user_id = :userId
                  AND t.transaction_type = 'DEBIT'
                  AND t.counterparty IS NOT NULL
                  AND t.transaction_time BETWEEN :from AND :to
                GROUP BY t.counterparty
                ORDER BY total_spend DESC
                LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> merchantBreakdownRaw(@Param("userId") UUID userId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("limit") int limit);

    // ── Transaction list (paginated + filtered) ───────────────────────────────
    @Query(value = """
                SELECT *
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_time BETWEEN :from AND :to
                  AND (:category IS NULL OR category = :category)
                  AND (:transactionType IS NULL OR transaction_type = :transactionType)
                  AND (:paymentMode IS NULL OR payment_mode = :paymentMode)
                  AND (:counterparty IS NULL OR counterparty ILIKE CONCAT('%', :counterparty, '%'))
                ORDER BY transaction_time DESC
            """,
            countQuery = """
                SELECT COUNT(*)
                FROM transactions
                WHERE user_id = :userId
                  AND transaction_time BETWEEN :from AND :to
                  AND (:category IS NULL OR category = :category)
                  AND (:transactionType IS NULL OR transaction_type = :transactionType)
                  AND (:paymentMode IS NULL OR payment_mode = :paymentMode)
                  AND (:counterparty IS NULL OR counterparty ILIKE CONCAT('%', :counterparty, '%'))
            """,
            nativeQuery = true)
    Page<Transaction> findFiltered(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("category") String category,
            @Param("transactionType") String transactionType,
            @Param("paymentMode") String paymentMode,
            @Param("counterparty") String counterparty,
            Pageable pageable);
}
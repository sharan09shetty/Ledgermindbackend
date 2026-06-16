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

    // ── Summary ──────────────────────────────────────────────────────────────

    @Query("""
                SELECT COALESCE(SUM(t.amount), 0)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionType = 'DEBIT'
                  AND t.transactionTime BETWEEN :from AND :to
            """)
    BigDecimal sumDebits(@Param("userId") UUID userId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    @Query("""
                SELECT COALESCE(SUM(t.amount), 0)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionType = 'CREDIT'
                  AND t.transactionTime BETWEEN :from AND :to
            """)
    BigDecimal sumCredits(@Param("userId") UUID userId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    @Query("""
                SELECT COUNT(t)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionTime BETWEEN :from AND :to
            """)
    long countTransactions(@Param("userId") UUID userId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    // Returns [category, total] — pick first row for topCategory
    @Query("""
                SELECT t.category, SUM(t.amount)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionType = 'DEBIT'
                  AND t.category IS NOT NULL
                  AND t.transactionTime BETWEEN :from AND :to
                GROUP BY t.category
                ORDER BY SUM(t.amount) DESC
            """)
    List<Object[]> topCategoryRaw(@Param("userId") UUID userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    // Returns [counterparty, total] — pick first row for topMerchant
    @Query("""
                SELECT t.counterparty, SUM(t.amount)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionType = 'DEBIT'
                  AND t.counterparty IS NOT NULL
                  AND t.transactionTime BETWEEN :from AND :to
                GROUP BY t.counterparty
                ORDER BY SUM(t.amount) DESC
            """)
    List<Object[]> topMerchantRaw(@Param("userId") UUID userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    // ── Monthly breakdown ─────────────────────────────────────────────────────

    @Query("""
            SELECT
                EXTRACT(YEAR FROM t.transactionTime),
                EXTRACT(MONTH FROM t.transactionTime),
                t.transactionType,
                SUM(t.amount),
                COUNT(t)
            FROM Transaction t
            WHERE t.userId = :userId
              AND t.transactionTime BETWEEN :from AND :to
            GROUP BY
                EXTRACT(YEAR FROM t.transactionTime),
                EXTRACT(MONTH FROM t.transactionTime),
                t.transactionType
            ORDER BY
                EXTRACT(YEAR FROM t.transactionTime),
                EXTRACT(MONTH FROM t.transactionTime)
            """)
    List<Object[]> monthlyRaw(@Param("userId") UUID userId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    // ── Category breakdown ────────────────────────────────────────────────────

    @Query("""
                SELECT t.category, SUM(t.amount), COUNT(t)
                FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionType = 'DEBIT'
                  AND t.category IS NOT NULL
                  AND t.transactionTime BETWEEN :from AND :to
                GROUP BY t.category
                ORDER BY SUM(t.amount) DESC
            """)
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
                        WHERE t2.user_id = :#{#userId}
                          AND t2.counterparty = t.counterparty
                          AND t2.transaction_type = 'DEBIT'
                          AND t2.transaction_time BETWEEN :from AND :to
                          AND t2.category IS NOT NULL
                        GROUP BY t2.category
                        ORDER BY COUNT(*) DESC
                        LIMIT 1
                    ) AS top_category
                FROM transactions t
                WHERE t.user_id = :#{#userId}
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

    @Query("""
                SELECT t FROM Transaction t
                WHERE t.userId = :userId
                  AND t.transactionTime BETWEEN :from AND :to
                  AND (:category IS NULL OR CAST(t.category AS string) = :category)
                  AND (:transactionType IS NULL OR CAST(t.transactionType AS string) = :transactionType)
                  AND (:paymentMode IS NULL OR CAST(t.paymentMode AS string) = :paymentMode)
                  AND (:counterparty IS NULL OR LOWER(t.counterparty) LIKE LOWER(CONCAT('%', :counterparty, '%')))
                ORDER BY t.transactionTime DESC
            """)
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
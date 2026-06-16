package com.ledgermind.ledgermindbackend.analytics.service;

import com.ledgermind.ledgermindbackend.analytics.dto.*;
import com.ledgermind.ledgermindbackend.analytics.repository.AnalyticsRepository;
import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsRepository repo;

    public SummaryResponse getSummary(UUID userId, LocalDateTime from, LocalDateTime to) {

        BigDecimal totalDebit  = repo.sumDebits(userId, from, to);
        BigDecimal totalCredit = repo.sumCredits(userId, from, to);
        long count             = repo.countTransactions(userId, from, to);

        List<Object[]> catRows      = repo.topCategoryRaw(userId, from, to);
        List<Object[]> merchantRows = repo.topMerchantRaw(userId, from, to);

        String topCategory     = catRows.isEmpty()      ? null : catRows.get(0)[0].toString();
        String topMerchant     = merchantRows.isEmpty() ? null : (String) merchantRows.get(0)[0];
        BigDecimal topMerchantSpend = merchantRows.isEmpty()
                ? BigDecimal.ZERO
                : (BigDecimal) merchantRows.get(0)[1];

        return SummaryResponse.builder()
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .net(totalCredit.subtract(totalDebit))
                .transactionCount(count)
                .topCategory(topCategory)
                .topMerchant(topMerchant)
                .topMerchantSpend(topMerchantSpend)
                .build();
    }

    // ── Monthly breakdown ─────────────────────────────────────────────────────

    public List<MonthlyBreakdownItem> getMonthlyBreakdown(UUID userId, LocalDateTime from, LocalDateTime to) {

        List<Object[]> rows = repo.monthlyRaw(userId, from, to);

        // rows: [year, month, transactionType, sum, count]
        // Merge DEBIT and CREDIT rows for the same year+month into one item
        Map<String, MonthlyBreakdownItem> map = new LinkedHashMap<>();

        for (Object[] row : rows) {
            int year    = ((Number) row[0]).intValue();
            int month   = ((Number) row[1]).intValue();
            String type = row[2].toString();
            BigDecimal amount = (BigDecimal) row[3];
            long txnCount     = ((Number) row[4]).longValue();

            String key = year + "-" + month;
            MonthlyBreakdownItem item = map.computeIfAbsent(key, k -> MonthlyBreakdownItem.builder()
                    .year(year)
                    .month(month)
                    .monthLabel(Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year)
                    .totalDebit(BigDecimal.ZERO)
                    .totalCredit(BigDecimal.ZERO)
                    .transactionCount(0)
                    .build());

            if (TransactionType.DEBIT.name().equals(type)) {
                item.setTotalDebit(amount);
            } else if (TransactionType.CREDIT.name().equals(type)) {
                item.setTotalCredit(amount);
            }
            item.setTransactionCount(item.getTransactionCount() + txnCount);
        }

        return new ArrayList<>(map.values());
    }

    // ── Category breakdown ────────────────────────────────────────────────────

    public List<CategoryBreakdownItem> getCategoryBreakdown(UUID userId, LocalDateTime from, LocalDateTime to) {

        List<Object[]> rows = repo.categoryBreakdownRaw(userId, from, to);

        BigDecimal grandTotal = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream().map(row -> {
            BigDecimal spend = (BigDecimal) row[1];
            double pct = grandTotal.compareTo(BigDecimal.ZERO) == 0
                    ? 0.0
                    : spend.divide(grandTotal, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            return CategoryBreakdownItem.builder()
                    .category(row[0].toString())
                    .totalSpend(spend)
                    .transactionCount(((Number) row[2]).longValue())
                    .percentageShare(Math.round(pct * 100.0) / 100.0)
                    .build();
        }).toList();
    }

    // ── Merchant breakdown ────────────────────────────────────────────────────

    public List<MerchantBreakdownItem> getMerchantBreakdown(UUID userId, LocalDateTime from, LocalDateTime to, int topN) {

        List<Object[]> rows = repo.merchantBreakdownRaw(userId, from, to, topN);

        return rows.stream().map(row -> MerchantBreakdownItem.builder()
                .merchant((String) row[0])
                .totalSpend((BigDecimal) row[1])
                .transactionCount(((Number) row[2]).longValue())
                .topCategory(row[3] != null ? row[3].toString() : null)
                .build()
        ).toList();
    }

    // ── Transactions (paginated) ──────────────────────────────────────────────

    public PagedResponse<TransactionResponse> getTransactions(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to,
            String category,
            String transactionType,
            String paymentMode,
            String counterparty,
            int page,
            int size) {

        Page<Transaction> result = repo.findFiltered(
                userId, from, to,
                category, transactionType, paymentMode, counterparty,
                PageRequest.of(page, size));

        List<TransactionResponse> content = result.getContent().stream()
                .map(t -> TransactionResponse.builder()
                        .id(t.getId())
                        .amount(t.getAmount())
                        .transactionType(t.getTransactionType())
                        .paymentMode(t.getPaymentMode())
                        .category(t.getCategory())
                        .counterparty(t.getCounterparty())
                        .referenceNumber(t.getReferenceNumber())
                        .transactionTime(t.getTransactionTime())
                        .build())
                .toList();

        return PagedResponse.<TransactionResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }
}
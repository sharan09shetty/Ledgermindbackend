package com.ledgermind.ledgermindbackend.analytics.controller;

import com.ledgermind.ledgermindbackend.analytics.dto.*;
import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Analytics", description = "Analytics APIs")
@RestController
@RequestMapping("/analytics/{userId}")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Overall summary for a date range.
     * GET /analytics/{userId}/summary?from=2025-01-01T00:00:00&to=2025-12-31T23:59:59
     */
    @GetMapping("/summary")
    public SummaryResponse getSummary(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return analyticsService.getSummary(userId, from, to);
    }

    /**
     * Month-by-month debit + credit totals.
     * GET /analytics/{userId}/monthly?from=2025-01-01T00:00:00&to=2025-12-31T23:59:59
     */
    @GetMapping("/monthly")
    public List<MonthlyBreakdownItem> getMonthly(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return analyticsService.getMonthlyBreakdown(userId, from, to);
    }

    /**
     * Spend per category, with percentage share.
     * GET /analytics/{userId}/categories?from=...&to=...
     */
    @GetMapping("/categories")
    public List<CategoryBreakdownItem> getCategories(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return analyticsService.getCategoryBreakdown(userId, from, to);
    }

    /**
     * Top merchants by spend, with their dominant category.
     * GET /analytics/{userId}/merchants?from=...&to=...&topN=10
     */
    @GetMapping("/merchants")
    public List<MerchantBreakdownItem> getMerchants(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") int topN) {

        return analyticsService.getMerchantBreakdown(userId, from, to, topN);
    }

    /**
     * Paginated transaction list with optional filters.
     * GET /analytics/{userId}/transactions
     *       ?from=...&to=...
     *       &category=FOOD          (optional)
     *       &transactionType=DEBIT  (optional)
     *       &paymentMode=UPI        (optional)
     *       &counterparty=swiggy    (optional, case-insensitive partial match)
     *       &page=0&size=20
     */
    @GetMapping("/transactions")
    public PagedResponse<TransactionResponse> getTransactions(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String counterparty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return analyticsService.getTransactions(
                userId, from, to,
                category, transactionType, paymentMode, counterparty,
                page, size);
    }
}
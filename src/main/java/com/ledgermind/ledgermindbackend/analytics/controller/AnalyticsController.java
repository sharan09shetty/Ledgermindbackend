package com.ledgermind.ledgermindbackend.analytics.controller;

import com.ledgermind.ledgermindbackend.analytics.dto.*;
import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public SummaryResponse getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        UUID userId = SecurityUtils.currentUserId();
        return analyticsService.getSummary(userId, from, to);
    }

    @GetMapping("/monthly")
    public List<MonthlyBreakdownItem> getMonthly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        UUID userId = SecurityUtils.currentUserId();
        return analyticsService.getMonthlyBreakdown(userId, from, to);
    }

    @GetMapping("/categories")
    public List<CategoryBreakdownItem> getCategories(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        UUID userId = SecurityUtils.currentUserId();
        return analyticsService.getCategoryBreakdown(userId, from, to);
    }

    @GetMapping("/merchants")
    public List<MerchantBreakdownItem> getMerchants(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") int topN) {

        UUID userId = SecurityUtils.currentUserId();
        return analyticsService.getMerchantBreakdown(userId, from, to, topN);
    }

    @GetMapping("/transactions")
    public PagedResponse<TransactionResponse> getTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String counterparty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = SecurityUtils.currentUserId();
        return analyticsService.getTransactions(
                userId, from, to,
                category, transactionType, paymentMode, counterparty,
                page, size);
    }
}
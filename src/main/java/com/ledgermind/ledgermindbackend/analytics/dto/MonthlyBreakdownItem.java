package com.ledgermind.ledgermindbackend.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBreakdownItem {
    private int year;
    private int month;          // 1-12
    private String monthLabel;  // "Jan 2025"
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private long transactionCount;
}
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
public class CategoryBreakdownItem {
    private String category;
    private BigDecimal totalSpend;
    private long transactionCount;
    private double percentageShare;  // out of total debit
}
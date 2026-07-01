package com.ledgermind.ledgermindbackend.analytics.dto;

import com.ledgermind.ledgermindbackend.email.enums.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyInsightResponse {
    private LocalDate date;
    private BigDecimal totalSpent;
    private BigDecimal totalReceived;
    private BigDecimal net;
    private long transactionCount;

    private Category topCategory;
    private BigDecimal topCategorySpend;

    private String topMerchant;
    private BigDecimal topMerchantSpend;

    private BigDecimal highestTransactionAmount;
    private String highestTransactionMerchant;
    private Category highestTransactionCategory;
}

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
public class SummaryResponse {
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal net;                  // credit - debit
    private long transactionCount;
    private String topCategory;
    private String topMerchant;
    private BigDecimal topCategorySpend;
    private BigDecimal topMerchantSpend;
}
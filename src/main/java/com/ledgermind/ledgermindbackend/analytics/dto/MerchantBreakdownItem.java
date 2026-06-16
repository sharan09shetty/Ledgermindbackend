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
public class MerchantBreakdownItem {
    private String merchant;
    private BigDecimal totalSpend;
    private long transactionCount;
    private String topCategory;
}
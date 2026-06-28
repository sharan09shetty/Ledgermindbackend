package com.ledgermind.ledgermindbackend.telegram.advisor;

import com.ledgermind.ledgermindbackend.analytics.dto.*;
import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Component
@Scope("prototype")
@RequiredArgsConstructor
@Slf4j
public class FinancialAdvisorTools {

    private final AnalyticsService analyticsService;

    private UUID userId;

    public FinancialAdvisorTools forUser(UUID userId) {
        this.userId = userId;
        return this;
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    private LocalDateTime startOf(int year, int month) {
        return YearMonth.of(year, month).atDay(1).atStartOfDay();
    }

    private LocalDateTime endOf(int year, int month) {
        return YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59);
    }

    // ── Tools ─────────────────────────────────────────────────────────────────

    @Tool(description = """
            Get a financial summary for a given month: total debits, total credits,
            net (credit - debit), transaction count, top spending category, and top merchant.
            Use for: 'how much did I spend this month?', 'what's my summary for June?'.
            If no month/year provided, use the current month.
            """)
    public SummaryResponse getMonthlySummary(
            @ToolParam(description = "4-digit year, e.g. 2026") Integer year,
            @ToolParam(description = "Month number 1-12") Integer month) {

        YearMonth target = (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);
        log.info("[FinancialAdvisor] tool=getMonthlySummary userId={} {}", userId, target);
        return analyticsService.getSummary(userId, startOf(target.getYear(), target.getMonthValue()), endOf(target.getYear(), target.getMonthValue()));
    }

    @Tool(description = """
            Get spending breakdown by category for a given month.
            Returns each category with total spend, transaction count, and percentage share.
            Use for: 'what did I spend on food last month?', 'show spending by category'.
            If no month/year provided, use the current month.
            """)
    public List<CategoryBreakdownItem> getCategoryBreakdown(
            @ToolParam(description = "4-digit year, e.g. 2026") Integer year,
            @ToolParam(description = "Month number 1-12") Integer month) {

        YearMonth target = (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);
        log.info("[FinancialAdvisor] tool=getCategoryBreakdown userId={} {}", userId, target);
        return analyticsService.getCategoryBreakdown(userId, startOf(target.getYear(), target.getMonthValue()), endOf(target.getYear(), target.getMonthValue()));
    }

    @Tool(description = """
            Get the top merchants by spend for a given month.
            Returns merchant name, total spend, transaction count, and their top category.
            Use for: 'where am I spending the most?', 'show my top 5 merchants'.
            If no month/year provided, use the current month.
            """)
    public List<MerchantBreakdownItem> getTopMerchants(
            @ToolParam(description = "4-digit year, e.g. 2026") Integer year,
            @ToolParam(description = "Month number 1-12") Integer month,
            @ToolParam(description = "Number of top merchants to return, default 5") Integer topN) {

        YearMonth target = (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);
        int safeTopN = (topN == null) ? 5 : topN;
        log.info("[FinancialAdvisor] tool=getTopMerchants userId={} {} topN={}", userId, target, safeTopN);
        return analyticsService.getMerchantBreakdown(userId, startOf(target.getYear(), target.getMonthValue()), endOf(target.getYear(), target.getMonthValue()), safeTopN);
    }

    @Tool(description = """
            Get month-by-month spending trend over a range of months.
            Returns total debits, credits, and transaction count per month.
            Use for: 'am I spending more than last month?', 'show trend over last 3 months'.
            """)
    public List<MonthlyBreakdownItem> getSpendingTrend(
            @ToolParam(description = "Start year, e.g. 2026") Integer fromYear,
            @ToolParam(description = "Start month number 1-12") Integer fromMonth,
            @ToolParam(description = "End year, e.g. 2026") Integer toYear,
            @ToolParam(description = "End month number 1-12") Integer toMonth) {

        YearMonth now = YearMonth.now();
        YearMonth from = (fromYear == null || fromMonth == null) ? now.minusMonths(3) : YearMonth.of(fromYear, fromMonth);
        YearMonth to = (toYear == null || toMonth == null) ? now : YearMonth.of(toYear, toMonth);
        log.info("[FinancialAdvisor] tool=getSpendingTrend userId={} from={} to={}", userId, from, to);
        return analyticsService.getMonthlyBreakdown(userId, startOf(from.getYear(), from.getMonthValue()), endOf(to.getYear(), to.getMonthValue()));
    }

    @Tool(description = """
            Get transactions between two specific dates (inclusive), optionally filtered
            by category, merchant name, or transaction type (DEBIT or CREDIT).
            
            ALWAYS use this tool when the user asks about a specific day or date range:
            - 'yesterday' → fromDate = yesterday, toDate = yesterday
            - 'today' → fromDate = today, toDate = today
            - 'last 3 days' → fromDate = 3 days ago, toDate = today
            - 'last week' → fromDate = 7 days ago, toDate = today
            - 'between 1st and 15th June' → fromDate = 2026-06-01, toDate = 2026-06-15
            
            Date format: YYYY-MM-DD (e.g. 2026-06-27).
            Leave filters null if not specified.
            Max 50 results returned.
            """)
    public PagedResponse<TransactionResponse> getTransactionsByDateRange(
            @ToolParam(description = "Start date inclusive, format YYYY-MM-DD") String fromDate,
            @ToolParam(description = "End date inclusive, format YYYY-MM-DD") String toDate,
            @ToolParam(description = "Category filter e.g. FOOD, TRAVEL, SHOPPING, BILLS, ENTERTAINMENT, HEALTH, INVESTMENT, SALARY, TRANSFER, OTHER. Null for all.") String category,
            @ToolParam(description = "Partial merchant name, e.g. 'Swiggy'. Null for all.") String merchant,
            @ToolParam(description = "DEBIT or CREDIT. Null for both.") String transactionType,
            @ToolParam(description = "Max results to return, default 20, max 50") Integer limit) {

        LocalDate from = (fromDate != null) ? LocalDate.parse(fromDate) : LocalDate.now();
        LocalDate to = (toDate != null) ? LocalDate.parse(toDate) : LocalDate.now();
        int safeLimit = (limit == null) ? 20 : Math.min(limit, 50);

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        log.info("[FinancialAdvisor] tool=getTransactionsByDateRange userId={} from={} to={} category={} merchant={} type={} limit={}",
                userId, fromDt, toDt, category, merchant, transactionType, safeLimit);

        return analyticsService.getTransactions(
                userId, fromDt, toDt,
                category, transactionType, null, merchant,
                0, safeLimit);
    }
}

package com.ledgermind.ledgermindbackend.user.service;

import com.ledgermind.ledgermindbackend.analytics.dto.DailyInsightResponse;
import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import com.ledgermind.ledgermindbackend.telegram.service.TelegramService;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Sends every active user a nightly Telegram summary of that day's spending:
 * total spent/received, transaction count, top category, and the single
 * biggest expense. Runs late at night IST, after the day's transactions have
 * had time to settle, and skips users with no activity that day so we don't
 * spam a "you spent ₹0 today" message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyInsightsSchedulerService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;
    private final TelegramService telegramService;

    @Scheduled(cron = "${ledgermind.insights.cron:0 30 21 * * *}", zone = "Asia/Kolkata")
    public void sendDailyInsights() {
        LocalDate today = LocalDate.now(IST);
        List<User> users = userRepository.findByActiveTrue();
        log.info("Sending daily insights to up to {} active users for {}", users.size(), today);

        int sent = 0;
        for (User user : users) {
            try {
                if (sendInsightIfAny(user, today)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("Failed to send daily insight to user {}", user.getId(), e);
            }
        }
        log.info("Daily insights complete: {} sent, {} skipped (no activity or unlinked)", sent, users.size() - sent);
    }

    private boolean sendInsightIfAny(User user, LocalDate date) {
        if (user.getTelegramChatId() == null) {
            // Shouldn't happen for active users given isReadyForScanning(), but guard anyway.
            return false;
        }

        DailyInsightResponse insight = analyticsService.getDailyInsight(user.getId(), date);
        log.debug("insight : "+insight);
        if (insight.getTransactionCount() == 0) {
            log.debug("No transactions for user {} on {}, skipping insight", user.getId(), date);
            return false;
        }

        telegramService.sendDailyInsight(user.getTelegramChatId(), insight);
        return true;
    }
}

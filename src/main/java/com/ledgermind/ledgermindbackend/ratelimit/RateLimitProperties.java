package com.ledgermind.ledgermindbackend.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-action rate limits. Each {@link Limit} is "at most N requests per
 * window" and is enforced per user. Bound from {@code ratelimit.*} — e.g.
 * {@code ratelimit.telegram-chat.limit=20}, {@code .window=1m}.
 */
@Component
@ConfigurationProperties(prefix = "ratelimit")
@Getter
@Setter
public class RateLimitProperties {

    private Limit telegramChat = new Limit(20, Duration.ofMinutes(1));
    private Limit telegramCashLog = new Limit(15, Duration.ofMinutes(1));

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limit {
        private int limit;
        private Duration window;
    }
}

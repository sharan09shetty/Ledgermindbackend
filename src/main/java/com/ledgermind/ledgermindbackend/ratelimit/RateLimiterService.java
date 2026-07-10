package com.ledgermind.ledgermindbackend.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed fixed-window rate limiter. The INCR + first-hit expiry run in a
 * single Lua script so the window can never be left without a TTL (which would
 * block a key forever). Keys expire on their own, so nothing needs cleanup.
 *
 * <p>Fails <em>open</em>: if Redis is unreachable we allow the request rather
 * than break user-facing features. The cost exposure of a brief Redis outage
 * is far smaller than blocking every user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:";

    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * Records one hit against {@code key} and reports whether it is still
     * within the allowed {@code limit} for the current {@code window}.
     *
     * @return true if allowed, false if the caller has exhausted its quota
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.execute(
                    INCR_WITH_TTL,
                    List.of(KEY_PREFIX + key),
                    String.valueOf(window.toMillis()));

            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("Rate limiter unavailable for key={}, allowing request: {}", key, e.getMessage());
            return true;
        }
    }
}

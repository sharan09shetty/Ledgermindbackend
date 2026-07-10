package com.ledgermind.ledgermindbackend.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RateLimiterServiceTest {

    @SuppressWarnings("unchecked")
    private RateLimiterService withCount(Long count) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(count);
        return new RateLimiterService(redis);
    }

    @Test
    void allowsWhenUnderLimit() {
        assertTrue(withCount(1L).tryAcquire("k", 5, Duration.ofMinutes(1)));
        assertTrue(withCount(5L).tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    void blocksWhenOverLimit() {
        assertFalse(withCount(6L).tryAcquire("k", 5, Duration.ofMinutes(1)));
        assertFalse(withCount(100L).tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    void failsOpenWhenRedisThrows() {
        @SuppressWarnings("unchecked")
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("redis down"));

        assertTrue(new RateLimiterService(redis).tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void namespacesKeyAndPassesWindowMillis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        new RateLimiterService(redis).tryAcquire("tg-chat:abc", 5, Duration.ofSeconds(30));

        verify(redis).execute(any(RedisScript.class),
                eq(List.of("ratelimit:tg-chat:abc")),
                eq("30000"));
    }
}

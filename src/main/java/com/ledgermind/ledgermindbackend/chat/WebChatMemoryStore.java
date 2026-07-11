package com.ledgermind.ledgermindbackend.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Conversation memory for the in-app advisor chat. Deliberately separate from
 * the Telegram chat memory (different key namespace, keyed by user id rather
 * than Telegram chat id, longer retention) so the two channels never bleed
 * into each other.
 *
 * Messages carry a timestamp so the UI can render times and decide when the
 * user has been away long enough to deserve a fresh greeting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ledgermind.webchat.memory.ttl-hours:168}")
    private int ttlHours;

    // 40 messages = the last 20 user/assistant exchanges shown in the UI
    @Value("${ledgermind.webchat.memory.max-messages:40}")
    private int maxMessages;

    private static final String KEY_PREFIX = "ledgermind:webchat:";

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    public List<ChatEntry> load(UUID userId) {
        try {
            String json = redisTemplate.opsForValue().get(key(userId));
            if (json == null) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[WebChatMemory] Failed to load history for userId={}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(UUID userId, List<ChatEntry> messages) {
        try {
            List<ChatEntry> trimmed = messages.size() > maxMessages
                    ? messages.subList(messages.size() - maxMessages, messages.size())
                    : messages;

            String json = objectMapper.writeValueAsString(trimmed);
            redisTemplate.opsForValue().set(key(userId), json, Duration.ofHours(ttlHours));
        } catch (Exception e) {
            log.warn("[WebChatMemory] Failed to save history for userId={}: {}", userId, e.getMessage());
        }
    }

    public void clear(UUID userId) {
        redisTemplate.delete(key(userId));
        log.info("[WebChatMemory] Cleared history for userId={}", userId);
    }

    /**
     * role: "user" or "assistant"; at: epoch millis (UTC)
     */
    public record ChatEntry(String role, String content, long at) {}
}

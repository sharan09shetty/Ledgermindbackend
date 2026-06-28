package com.ledgermind.ledgermindbackend.telegram.advisor;

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

/**
 * Stores conversation history in Redis as a JSON list of {role, content} maps.
 * Each chatId gets its own key with a configurable TTL.
 *
 * We store raw role/content pairs rather than Spring AI Message objects
 * to keep the Redis payload simple and avoid serialization coupling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ledgermind.advisor.memory.ttl-hours:24}")
    private int ttlHours;

    @Value("${ledgermind.advisor.memory.max-messages:20}")
    private int maxMessages;

    private static final String KEY_PREFIX = "ledgermind:chat:";

    private String key(Long chatId) {
        return KEY_PREFIX + chatId;
    }

    public List<ChatMessage> load(Long chatId) {
        try {
            String json = redisTemplate.opsForValue().get(key(chatId));
            if (json == null) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[ChatMemory] Failed to load history for chatId={}: {}", chatId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(Long chatId, List<ChatMessage> messages) {
        try {
            List<ChatMessage> trimmed = messages.size() > maxMessages
                    ? messages.subList(messages.size() - maxMessages, messages.size())
                    : messages;

            String json = objectMapper.writeValueAsString(trimmed);
            redisTemplate.opsForValue().set(key(chatId), json, Duration.ofHours(ttlHours));
        } catch (Exception e) {
            log.warn("[ChatMemory] Failed to save history for chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void clear(Long chatId) {
        redisTemplate.delete(key(chatId));
        log.info("[ChatMemory] Cleared history for chatId={}", chatId);
    }

    /**
     * Simple role/content pair — avoids coupling to Spring AI internals.
     * role: "user" or "assistant"
     */
    public record ChatMessage(String role, String content) {}
}

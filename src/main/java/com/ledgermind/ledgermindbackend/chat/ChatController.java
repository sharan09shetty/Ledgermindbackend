package com.ledgermind.ledgermindbackend.chat;

import com.ledgermind.ledgermindbackend.chat.WebChatMemoryStore.ChatEntry;
import com.ledgermind.ledgermindbackend.ratelimit.RateLimitProperties;
import com.ledgermind.ledgermindbackend.ratelimit.RateLimiterService;
import com.ledgermind.ledgermindbackend.security.SecurityUtils;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final WebChatService webChatService;
    private final UserRepository userRepository;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;

    public record ChatMessageRequest(String message) {}
    public record ChatHistoryResponse(List<ChatEntry> messages) {}

    @GetMapping("/history")
    public ResponseEntity<ChatHistoryResponse> history() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(new ChatHistoryResponse(webChatService.history(userId)));
    }

    @PostMapping("/message")
    public ResponseEntity<ChatEntry> send(@RequestBody ChatMessageRequest request) {
        UUID userId = SecurityUtils.currentUserId();

        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must not be empty");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message too long (max " + MAX_MESSAGE_LENGTH + " characters)");
        }

        RateLimitProperties.Limit limit = rateLimitProperties.getWebChat();
        boolean allowed = rateLimiterService.tryAcquire(
                "web-chat:" + userId, limit.getLimit(), limit.getWindow());
        if (!allowed) {
            log.info("[WebChat] Rate limited userId={}", userId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You're sending messages a little fast — give it a minute and try again.");
        }

        String name = userRepository.findById(userId).map(User::getName).orElse(null);
        return ResponseEntity.ok(webChatService.ask(userId, name, message));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clear() {
        UUID userId = SecurityUtils.currentUserId();
        webChatService.clear(userId);
        return ResponseEntity.noContent().build();
    }
}

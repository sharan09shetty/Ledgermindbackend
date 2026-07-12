package com.ledgermind.ledgermindbackend.telegram.service;

import com.ledgermind.ledgermindbackend.telegram.advisor.RedisChatMemoryStore;
import com.ledgermind.ledgermindbackend.telegram.config.TelegramProperties;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramLinkResponse;
import com.ledgermind.ledgermindbackend.telegram.dto.TelegramMessageRequest;
import com.ledgermind.ledgermindbackend.telegram.entity.TelegramLink;
import com.ledgermind.ledgermindbackend.telegram.repository.TelegramLinkRepository;
import com.ledgermind.ledgermindbackend.user.entity.User;
import com.ledgermind.ledgermindbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private static final String CLOSED_NEW_DEVICE_MESSAGE =
            "This LedgerMind session was closed because your account was linked to a new Telegram chat. " +
                    "If this wasn't you, please log into the app and re-link Telegram to regain access.";

    private static final String CLOSED_UNLINK_MESSAGE =
            "Your Telegram has been unlinked from LedgerMind. You'll need to reconnect from the app to keep getting transaction updates.";

    private final TelegramLinkRepository telegramLinkRepository;
    private final UserRepository userRepository;
    private final TelegramService telegramService;
    private final RedisChatMemoryStore memoryStore;
    private final TelegramProperties telegramProperties;

    public enum ClaimStatus {
        SUCCESS,
        INVALID_OR_EXPIRED,
        CHAT_ALREADY_LINKED
    }

    public record ClaimResult(ClaimStatus status, User user) {
        public boolean isSuccess() {
            return status == ClaimStatus.SUCCESS;
        }
    }

    /**
     * Generates a fresh, random, single-use, short-lived link token for the
     * given user and returns the Telegram deep link built from it.
     * Overwrites any previously-issued unclaimed token for this user, so
     * only the most recently requested link is ever valid.
     * <p>
     * Deliberately NOT @Transactional: two concurrent requests for the same
     * user (double-fired client call, double click) can both see "no row yet"
     * and race to insert it. The loser hits the unique constraint on user_id;
     * we catch that and retry, which then finds the winner's committed row
     * and simply overwrites its token.
     */
    public TelegramLinkResponse generateLinkToken(UUID userId) {
        try {
            return upsertLinkToken(userId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Concurrent Telegram link-token requests for userId={}, retrying as update", userId);
            return upsertLinkToken(userId);
        }
    }

    private TelegramLinkResponse upsertLinkToken(UUID userId) {
        String token = randomToken();
        Instant expiresAtInstant = Instant.now().plus(TOKEN_TTL);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(expiresAtInstant, ZoneId.systemDefault());

        TelegramLink link = telegramLinkRepository.findByUserId(userId)
                .orElseGet(() -> TelegramLink.builder().userId(userId).build());

        link.setLinkToken(token);
        link.setLinkTokenExpiresAt(expiresAt);
        telegramLinkRepository.saveAndFlush(link);

        String deepLink = "https://t.me/" + telegramProperties.getBotUsername() + "?start=" + token;
        log.info("Generated Telegram link token for userId={}, expiresAt={}", userId, expiresAtInstant);
        return new TelegramLinkResponse(deepLink, expiresAtInstant);
    }

    /**
     * Attempts to claim a link token from an incoming /start message. Uses a
     * row lock (see TelegramLinkRepository#findByLinkTokenForUpdate) so the
     * claim is safe even if the same token is replayed concurrently.
     * <p>
     * If the user already had a different chat linked (i.e. this is a
     * re-link from a new device), the old session is closed: a notice is
     * sent to the old chat and its Redis conversation history is cleared,
     * so exactly one Telegram session is ever active per user.
     */
    @Transactional
    public ClaimResult claimToken(String token, String chatId) {
        if (token == null || token.isBlank()) {
            return new ClaimResult(ClaimStatus.INVALID_OR_EXPIRED, null);
        }

        TelegramLink link = telegramLinkRepository.findByLinkTokenForUpdate(token).orElse(null);
        if (link == null || link.getLinkTokenExpiresAt() == null
                || link.getLinkTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return new ClaimResult(ClaimStatus.INVALID_OR_EXPIRED, null);
        }

        User user = userRepository.findById(link.getUserId()).orElse(null);
        if (user == null) {
            return new ClaimResult(ClaimStatus.INVALID_OR_EXPIRED, null);
        }

        Optional<TelegramLink> chatOwner = telegramLinkRepository.findByChatId(chatId);
        if (chatOwner.isPresent() && !chatOwner.get().getUserId().equals(link.getUserId())) {
            log.warn("Rejected Telegram link claim: chatId={} already linked to a different user", chatId);
            return new ClaimResult(ClaimStatus.CHAT_ALREADY_LINKED, user);
        }

        String previousChatId = link.getChatId();

        link.setChatId(chatId);
        link.setLinkToken(null);
        link.setLinkTokenExpiresAt(null);
        link.setLinkedAt(LocalDateTime.now());
        telegramLinkRepository.save(link);

        if (user.isReadyForScanning()) {
            user.setActive(true);
            userRepository.save(user);
        }

        log.info("Telegram linked for userId={}, chatId={}", user.getId(), chatId);

        if (previousChatId != null && !previousChatId.equals(chatId)) {
            log.info("Closing previous Telegram session chatId={} for userId={} (relinked to chatId={})",
                    previousChatId, user.getId(), chatId);
            closeSession(previousChatId, CLOSED_NEW_DEVICE_MESSAGE);
        }

        return new ClaimResult(ClaimStatus.SUCCESS, user);
    }

    /**
     * Explicit unlink, e.g. triggered from the web UI. Clears the chat
     * link (and any pending unclaimed token) and closes the Telegram side
     * the same way a forced re-link would.
     */
    @Transactional
    public void unlink(UUID userId) {
        TelegramLink link = telegramLinkRepository.findByUserId(userId).orElse(null);
        if (link == null || link.getChatId() == null) {
            return;
        }

        String chatId = link.getChatId();

        link.setChatId(null);
        link.setLinkToken(null);
        link.setLinkTokenExpiresAt(null);
        telegramLinkRepository.save(link);

        userRepository.findById(userId).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getActive())) {
                user.setActive(false);
                userRepository.save(user);
            }
        });

        log.info("Telegram unlinked for userId={}, chatId={}", userId, chatId);
        closeSession(chatId, CLOSED_UNLINK_MESSAGE);
    }

    /**
     * Resolves the User that owns a given Telegram chat, used by every
     * webhook handler that previously did userRepository.findByTelegramChatId.
     */
    public Optional<User> resolveUserByChatId(String chatId) {
        return telegramLinkRepository.findByChatId(chatId)
                .flatMap(link -> userRepository.findById(link.getUserId()));
    }

    public Optional<String> resolveChatId(UUID userId) {
        return telegramLinkRepository.findByUserId(userId)
                .map(TelegramLink::getChatId);
    }

    public boolean isLinked(UUID userId) {
        return resolveChatId(userId).isPresent();
    }

    private void closeSession(String chatId, String message) {
        try {
            telegramService.sendMessage(TelegramMessageRequest.builder()
                    .chat_id(chatId)
                    .text(message)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send session-closed notice to chatId={}", chatId, e);
        }
        try {
            memoryStore.clear(Long.valueOf(chatId));
        } catch (Exception e) {
            log.warn("Failed to clear chat memory for chatId={}", chatId, e);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
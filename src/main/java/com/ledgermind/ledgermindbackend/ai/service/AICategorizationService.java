package com.ledgermind.ledgermindbackend.ai.service;

import com.ledgermind.ledgermindbackend.email.entity.Transaction;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AICategorizationService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MILLIS = {2_000, 5_000};

    /**
     * Thrown when the LLM provider is rate-limiting us. Propagated (rather
     * than swallowed into Category.OTHER) so the email is retried later
     * instead of being permanently mis-categorized.
     */
    public static class AiThrottledException extends RuntimeException {
        public AiThrottledException(Throwable cause) {
            super("LLM provider throttled categorization request", cause);
        }
    }

    private final ChatClient chatClient;

    public AICategorizationService(@Qualifier("categorizationChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Category categorize(Transaction transaction) {
        for (int attempt = 1; ; attempt++) {
            String response = "";
            try {
                response = chatClient.prompt()
                        .user(buildPrompt(transaction))
                        .call()
                        .content();
                log.info("Categorization response for merchant={} => {}", transaction.getCounterparty(), response);
                return Category.valueOf(response.trim().toUpperCase());
            } catch (Exception e) {
                if (isThrottling(e)) {
                    if (attempt >= MAX_ATTEMPTS) {
                        log.warn("Categorization throttled after {} attempts for merchant={}, deferring for retry",
                                attempt, transaction.getCounterparty());
                        throw new AiThrottledException(e);
                    }
                    long pause = BACKOFF_MILLIS[Math.min(attempt - 1, BACKOFF_MILLIS.length - 1)];
                    log.warn("Categorization throttled (attempt {}/{}) for merchant={}, backing off {}ms",
                            attempt, MAX_ATTEMPTS, transaction.getCounterparty(), pause);
                    sleep(pause);
                    continue;
                }
                log.error("Failed to categorize transaction for merchant={}, response='{}'",
                        transaction.getCounterparty(), response, e);
                return Category.OTHER;
            }
        }
    }

    /**
     * Provider-agnostic throttling detection: walks the cause chain looking
     * for a throttling/429-shaped exception, so it works for Bedrock's
     * ThrottlingException as well as OpenAI/Gemini HTTP 429s without
     * compile-time dependencies on any single provider SDK.
     */
    private boolean isThrottling(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String cls = t.getClass().getSimpleName();
            String msg = t.getMessage() == null ? "" : t.getMessage();
            if (cls.contains("Throttling")
                    || cls.contains("TooManyRequests")
                    || msg.contains("Status Code: 429")
                    || msg.contains("429 Too Many Requests")
                    || msg.toLowerCase().contains("too many requests")) {
                return true;
            }
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildPrompt(Transaction transaction) {
        return """
                You are a personal finance categorization assistant.

                Merchant: %s
                Transaction Type: %s
                Amount: %s

                Categories:
                %s

                Return ONLY the category name, nothing else.
                """.formatted(
                transaction.getCounterparty(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                Category.namesCsv());
    }
}

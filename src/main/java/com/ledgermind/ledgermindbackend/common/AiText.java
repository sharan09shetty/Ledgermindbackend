package com.ledgermind.ledgermindbackend.common;

import java.util.regex.Pattern;

/**
 * Cleanup for raw LLM output before it reaches users. Some models leak
 * chain-of-thought markup (e.g. {@code <thinking>...</thinking>}) into the
 * assistant text; users must never see it.
 */
public final class AiText {

    private static final Pattern THINKING_BLOCK =
            Pattern.compile("<thinking>.*?</thinking>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // An unclosed tag (model got cut off) — drop from the tag to the end.
    private static final Pattern DANGLING_THINKING =
            Pattern.compile("<thinking>.*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private AiText() {}

    public static String stripThinking(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = THINKING_BLOCK.matcher(text).replaceAll("");
        cleaned = DANGLING_THINKING.matcher(cleaned).replaceAll("");
        return cleaned.strip();
    }
}

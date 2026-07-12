package com.ledgermind.ledgermindbackend.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires up the AI chat clients. Categorization and the Telegram advisor chat
 * are separate concerns with different cost/quality trade-offs, so each gets
 * its own {@link ChatClient} that can point at a different provider and model:
 *
 * <pre>
 *   ai.categorization.provider / ai.categorization.model
 *   ai.chat.provider           / ai.chat.model
 * </pre>
 *
 * Provider is one of {@code openai}, {@code gemini}, {@code ollama},
 * {@code bedrock}. Each corresponds to a Spring-AI auto-configured
 * {@link ChatModel} bean; only the providers whose API keys/config are present
 * actually get a bean, so an unconfigured provider fails fast here with a
 * clear message rather than at first call. Leaving the model blank uses that
 * provider's default model from its
 * {@code spring.ai.<provider>.chat.options.model} property.
 *
 * <p>{@code bedrock} uses the AWS Bedrock Converse API (tool calling
 * supported) and authenticates via the standard AWS credentials chain — an
 * IAM role when deployed on AWS, env vars / profile locally. Bedrock model
 * IDs are the {@code anthropic.}-prefixed native IDs, usually behind a
 * cross-region inference profile (e.g.
 * {@code global.anthropic.claude-haiku-4-5-20251001-v1:0}).
 */
@Configuration
@Slf4j
public class AIConfig {

    /** provider key → Spring-AI ChatModel bean name. */
    private static final Map<String, String> PROVIDER_BEAN_NAMES = Map.of(
            "openai", "openAiChatModel",
            "gemini", "googleGenAiChatModel",
            "ollama", "ollamaChatModel",
            "bedrock", "bedrockProxyChatModel"
    );

    @Bean
    public ChatClient categorizationChatClient(
            Map<String, ChatModel> chatModels,
            @Value("${ai.categorization.provider}") String provider,
            @Value("${ai.categorization.model:}") String model) {
        return buildClient("categorization", chatModels, provider, model);
    }

    @Bean
    public ChatClient chatChatClient(
            Map<String, ChatModel> chatModels,
            @Value("${ai.chat.provider}") String provider,
            @Value("${ai.chat.model:}") String model) {
        return buildClient("chat", chatModels, provider, model);
    }

    /**
     * @param chatModels all ChatModel beans Spring created, keyed by bean name.
     *                   Providers that weren't configured are simply absent.
     */
    private ChatClient buildClient(String role, Map<String, ChatModel> chatModels,
                                   String provider, String modelOverride) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase();
        String beanName = PROVIDER_BEAN_NAMES.get(normalized);
        if (beanName == null) {
            throw new IllegalStateException("Unsupported AI provider '" + provider + "' for " + role
                    + ". Supported providers: " + PROVIDER_BEAN_NAMES.keySet());
        }

        ChatModel chatModel = chatModels.get(beanName);
        if (chatModel == null) {
            throw new IllegalStateException("AI provider '" + normalized + "' selected for " + role
                    + " but no '" + beanName + "' bean exists. Configure that provider's API key/settings.");
        }

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (modelOverride != null && !modelOverride.isBlank()) {
            builder.defaultOptions(ChatOptions.builder().model(modelOverride.trim()).build());
        }

        builder.defaultAdvisors(new SimpleLoggerAdvisor());

        log.info("AI {} client → provider={}, model={}", role, normalized,
                (modelOverride == null || modelOverride.isBlank()) ? "<provider default>" : modelOverride.trim());
        return builder.build();
    }
}

package com.ledgermind.ledgermindbackend.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
    public ChatClient geminiChatClient(
            @Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")
    public ChatClient ollamaChatClient(
            @Qualifier("ollamaChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
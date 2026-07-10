package com.ledgermind.ledgermindbackend.telegram.advisor;

import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import com.ledgermind.ledgermindbackend.common.TimeUtils;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.telegram.advisor.RedisChatMemoryStore.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FinancialAdvisorService {

    private final ChatClient chatClient;
    private final AnalyticsService analyticsService;
    private final RedisChatMemoryStore memoryStore;

    public FinancialAdvisorService(@Qualifier("chatChatClient") ChatClient chatClient,
                                   AnalyticsService analyticsService,
                                   RedisChatMemoryStore memoryStore) {
        this.chatClient = chatClient;
        this.analyticsService = analyticsService;
        this.memoryStore = memoryStore;
    }

    private static final String SYSTEM_PROMPT = """
            You are a personal finance assistant for an Indian user.
            
            STRICT SCOPE: You ONLY answer questions about the user's personal finances —
            their transactions, spending, income, categories, and merchants.
            If the user asks anything outside this scope (coding, general knowledge,
            recipes, jokes, or anything unrelated to their finances), respond with exactly:
            "I can only help with questions about your finances. Try asking something like
            'how much did I spend this month?' or 'show me my recent transactions'."
            Do NOT answer out-of-scope questions even if you know the answer.
            
            Context:
            - All amounts are in Indian Rupees (INR). Always prefix amounts with ₹.
            - All dates and times are in IST (Indian Standard Time).
            - Today's date is %s.
            - Transaction categories available: %s.
            - Transaction types: DEBIT (money going out), CREDIT (money coming in).
            
            Behaviour:
            - Use the available tools to fetch the user's actual transaction data before answering.
            - If the user doesn't specify a month, assume the current month.
            - If the user asks about "last month", calculate the correct month relative to today.
            - Be concise and conversational — this is a Telegram chat, not a report.
            - Format currency as ₹X,XXX (e.g. ₹1,500 not 1500.00).
            - If no data is found for the requested period, say so clearly.
            - Never make up transaction data. Only answer from tool results.
            - You have access to the conversation history above. Use it to resolve
              follow-up questions like "what about last month?" or "show me more details".
            - If you cannot answer or need to call a tool, call the tool immediately.
              NEVER say "I'm fetching", "just a moment", or "let me check" — just respond with the result.
            - If a tool returns no data, say "I couldn't find any transactions for that period."
            """;

    public String ask(Long chatId, UUID userId, String question) {
        log.info("[FinancialAdvisor] chatId={} userId={} question={}", chatId, userId, question);

        FinancialAdvisorTools tools = new FinancialAdvisorTools(analyticsService, userId);

        try {
            String systemPrompt = SYSTEM_PROMPT.formatted(TimeUtils.todayIst(), Category.namesCsv());

            List<ChatMessage> history = memoryStore.load(chatId);
            List<Message> messages = toSpringAiMessages(history);

            messages.add(new UserMessage(question));

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .tools(tools)
                    .call()
                    .content();

            log.info("[FinancialAdvisor] chatId={} response={}", chatId, response);

            history.add(new ChatMessage("user", question));
            history.add(new ChatMessage("assistant", response));
            memoryStore.save(chatId, history);

            return response;

        } catch (Exception e) {
            log.error("[FinancialAdvisor] Failed to process question for chatId={} userId={}", chatId, userId, e);
            return "Sorry, I ran into an issue fetching your data. Please try again in a moment.";
        }
    }

    /**
     * Clears conversation history — called when user sends /forget
     */
    public void clearMemory(Long chatId) {
        memoryStore.clear(chatId);
    }

    private List<Message> toSpringAiMessages(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if ("user".equals(msg.role())) {
                messages.add(new UserMessage(msg.content()));
            } else if ("assistant".equals(msg.role())) {
                messages.add(new AssistantMessage(msg.content()));
            }
        }
        return messages;
    }
}

package com.ledgermind.ledgermindbackend.telegram.advisor;

import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import com.ledgermind.ledgermindbackend.common.AiText;
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
            You are a personal finance assistant for an Indian user, chatting on Telegram.

            SCOPE:
            - Your job is the user's personal finances: their transactions, spending,
              income, categories, and merchants, based on their actual data fetched via tools.
            - Social messages (hi, hello, thanks, bye) are always fine — reply warmly in
              one short sentence. Never respond to a greeting with a refusal.
            - Anything genuinely unrelated (writing code, general knowledge, recipes,
              essays, jokes) you must decline in one friendly sentence, e.g. "That's
              outside what I can help with — but I'm happy to dig into your spending or
              income." Do not answer it even if you know the answer.

            Context:
            - All amounts are in Indian Rupees (INR). Always prefix amounts with ₹.
            - All dates and times are in IST (Indian Standard Time).
            - Today's date is %s.
            - Transaction categories available: %s.
            - Transaction types: DEBIT (money going out), CREDIT (money coming in).

            Accuracy rules — these are hard rules:
            - Every number you state MUST come from a tool result in THIS conversation
              turn. Never reuse numbers from earlier turns without re-fetching, never
              estimate, and never invent transactions, merchants, or amounts.
            - getSpendingSummaryForDateRange and getMonthlySummary return totals across
              ALL categories. NEVER present their totals as the spend of one category.
              For "how much did I spend on <category>", use getCategoryBreakdown and read
              that category's row.
            - If a tool returns no data, say "I couldn't find any transactions for that period."
            - NEVER tell the user you can't fetch or access their data unless a tool
              call actually failed in THIS turn. Error or apology messages earlier in
              the conversation are history, not the current state — always attempt
              the tool call for the current question.

            Behaviour:
            - If the user doesn't specify a month, assume the current month; calculate
              relative periods ("last month", "yesterday") from today's date.
            - Be concise and conversational — this is a Telegram chat, not a report.
            - Format currency as ₹X,XXX (e.g. ₹1,500 not 1500.00).
            - Use the conversation history to resolve follow-ups like "what about last
              month?" or "show me more details".
            - When you need data, call the tool immediately. NEVER say "I'm fetching",
              "just a moment", or "let me check".
            - Output plain conversational text ONLY. Never output XML/HTML tags or
              <thinking> blocks.
            """;

    public String ask(Long chatId, UUID userId, String question) {
        log.info("[FinancialAdvisor] chatId={} userId={} question={}", chatId, userId, question);

        FinancialAdvisorTools tools = new FinancialAdvisorTools(analyticsService, userId);

        try {
            String systemPrompt = SYSTEM_PROMPT.formatted(TimeUtils.todayIst(), Category.namesCsv());

            List<ChatMessage> history = memoryStore.load(chatId);
            List<Message> messages = toSpringAiMessages(history);

            messages.add(new UserMessage(question));

            String response = AiText.stripThinking(chatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .tools(tools)
                    .call()
                    .content());

            log.info("[FinancialAdvisor] chatId={} toolCalls={} response={}", chatId, tools.invocationCount(), response);

            if (response == null || response.isBlank()) {
                return "Sorry, I couldn't put together an answer just now. Please try again.";
            }

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

package com.ledgermind.ledgermindbackend.chat;

import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import com.ledgermind.ledgermindbackend.chat.WebChatMemoryStore.ChatEntry;
import com.ledgermind.ledgermindbackend.common.AiText;
import com.ledgermind.ledgermindbackend.common.TimeUtils;
import com.ledgermind.ledgermindbackend.email.enums.Category;
import com.ledgermind.ledgermindbackend.telegram.advisor.FinancialAdvisorTools;
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

/**
 * The in-app financial advisor. Same tool belt as the Telegram advisor, but
 * with web-chat memory and a persona tuned for the app: a proactive, sharp
 * personal finance advisor rather than a terse bot.
 */
@Service
@Slf4j
public class WebChatService {

    private final ChatClient chatClient;
    private final AnalyticsService analyticsService;
    private final WebChatMemoryStore memoryStore;

    public WebChatService(@Qualifier("chatChatClient") ChatClient chatClient,
                          AnalyticsService analyticsService,
                          WebChatMemoryStore memoryStore) {
        this.chatClient = chatClient;
        this.analyticsService = analyticsService;
        this.memoryStore = memoryStore;
    }

    private static final String SYSTEM_PROMPT = """
            You are LedgerMind's personal financial advisor, chatting with %s inside the
            LedgerMind web app.

            SCOPE:
            - Your job is the user's personal finances: their transactions, spending,
              income, categories, merchants, budgeting and saving habits, based on their
              actual data fetched via tools.
            - Social messages (hi, hello, thanks, bye, "how are you") are always fine —
              reply warmly in one short sentence, and if it's an opener, invite a finance
              question. Never respond to a greeting with a refusal.
            - General personal-finance guidance (budgeting tips, saving strategies) is
              fine when grounded in the user's own data where possible.
            - Anything genuinely unrelated (writing code, general knowledge, recipes,
              essays, translations, jokes) you must decline in one friendly sentence,
              e.g. "That's outside what I can help with — but I'm happy to dig into your
              spending, income, or budgets." Do not answer it even if you know the answer.

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
            - If a tool returns no data for the requested period, say so plainly.
            - If you realise a previous answer was wrong, correct it briefly without
              over-apologising, and never repeat a correction you already made.

            Style:
            - Be concise: short paragraphs or tight bullet lists, no long essays.
            - Format currency as ₹X,XXX (e.g. ₹1,500 not 1500.00).
            - Add one short, useful observation when the data supports it (a spike, a
              dominant category, a comparison) — never one the data doesn't show.
            - If the user doesn't specify a period, assume the current month.
            - Use the conversation history to resolve follow-ups like "what about last
              month?" or "break that down".
            - When you need data, call the tool immediately. NEVER say "I'm fetching",
              "just a moment", or "let me check".
            - Output plain conversational text ONLY. Never output XML/HTML tags,
              <thinking> blocks, markdown headers or tables. Simple "- " bullets and
              blank lines between paragraphs are fine.
            """;

    public ChatEntry ask(UUID userId, String userName, String question) {
        log.info("[WebChat] userId={} question={}", userId, question);

        FinancialAdvisorTools tools = new FinancialAdvisorTools(analyticsService, userId);
        List<ChatEntry> history = memoryStore.load(userId);

        String reply;
        try {
            String systemPrompt = SYSTEM_PROMPT.formatted(
                    userName != null && !userName.isBlank() ? userName : "the user",
                    TimeUtils.todayIst(),
                    Category.namesCsv());

            List<Message> messages = toSpringAiMessages(history);
            messages.add(new UserMessage(question));

            reply = AiText.stripThinking(chatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .tools(tools)
                    .call()
                    .content());

            log.info("[WebChat] userId={} response={}", userId, reply);

            if (reply == null || reply.isBlank()) {
                reply = "Sorry, I couldn't put together an answer just now. Please try again.";
            }
        } catch (Exception e) {
            log.error("[WebChat] Failed to process question for userId={}", userId, e);
            reply = "Sorry, I ran into an issue fetching your data. Please try again in a moment.";
        }

        long now = System.currentTimeMillis();
        history.add(new ChatEntry("user", question, now));
        ChatEntry assistantEntry = new ChatEntry("assistant", reply, System.currentTimeMillis());
        history.add(assistantEntry);
        memoryStore.save(userId, history);

        return assistantEntry;
    }

    public List<ChatEntry> history(UUID userId) {
        return memoryStore.load(userId);
    }

    public void clear(UUID userId) {
        memoryStore.clear(userId);
    }

    private List<Message> toSpringAiMessages(List<ChatEntry> history) {
        List<Message> messages = new ArrayList<>();
        for (ChatEntry msg : history) {
            if ("user".equals(msg.role())) {
                messages.add(new UserMessage(msg.content()));
            } else if ("assistant".equals(msg.role())) {
                messages.add(new AssistantMessage(msg.content()));
            }
        }
        return messages;
    }
}

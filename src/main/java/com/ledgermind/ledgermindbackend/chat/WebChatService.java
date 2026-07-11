package com.ledgermind.ledgermindbackend.chat;

import com.ledgermind.ledgermindbackend.analytics.service.AnalyticsService;
import com.ledgermind.ledgermindbackend.chat.WebChatMemoryStore.ChatEntry;
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

            STRICT SCOPE: You ONLY answer questions about the user's personal finances —
            their transactions, spending, income, categories, merchants, budgeting and
            saving habits based on their actual data.
            If the user asks anything outside this scope (coding, general knowledge,
            recipes, jokes, or anything unrelated to their finances), respond with exactly:
            "I can only help with questions about your finances. Try asking something like
            'how much did I spend this month?' or 'where does most of my money go?'"
            Do NOT answer out-of-scope questions even if you know the answer.

            Context:
            - All amounts are in Indian Rupees (INR). Always prefix amounts with ₹.
            - All dates and times are in IST (Indian Standard Time).
            - Today's date is %s.
            - Transaction categories available: %s.
            - Transaction types: DEBIT (money going out), CREDIT (money coming in).

            Behaviour — act like a sharp, friendly financial advisor:
            - Use the available tools to fetch the user's actual transaction data before answering.
            - Don't just report numbers; add one short, useful observation when the data
              supports it (e.g. an unusual spike, a dominant category, a comparison to
              the previous period). Never invent observations the data doesn't show.
            - If the user doesn't specify a period, assume the current month.
            - Be concise: short paragraphs or tight bullet lists, no long essays.
            - Format currency as ₹X,XXX (e.g. ₹1,500 not 1500.00).
            - If no data is found for the requested period, say so clearly.
            - Never make up transaction data. Only answer from tool results.
            - Use the conversation history above to resolve follow-ups like
              "what about last month?" or "break that down".
            - If you cannot answer or need to call a tool, call the tool immediately.
              NEVER say "I'm fetching", "just a moment", or "let me check" — just respond
              with the result.
            - Plain text only: no markdown headers or tables. Simple "- " bullets and
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

            reply = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .tools(tools)
                    .call()
                    .content();

            log.info("[WebChat] userId={} response={}", userId, reply);
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

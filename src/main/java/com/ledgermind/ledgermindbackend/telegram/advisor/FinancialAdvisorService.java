package com.ledgermind.ledgermindbackend.telegram.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialAdvisorService {

    private final ChatClient chatClient;
    private final FinancialAdvisorTools financialAdvisorTools;

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
            - Transaction categories available: FOOD, TRAVEL, ENTERTAINMENT, SHOPPING,
              BILLS, INVESTMENT, SALARY, TRANSFER, HEALTH, OTHER.
            - Transaction types: DEBIT (money going out), CREDIT (money coming in).
            
            Behaviour:
            - Use the available tools to fetch the user's actual transaction data before answering.
            - If the user doesn't specify a month, assume the current month.
            - If the user asks about "last month", calculate the correct month relative to today.
            - Be concise and conversational — this is a Telegram chat, not a report.
            - Format currency as ₹X,XXX (e.g. ₹1,500 not 1500.00).
            - If no data is found for the requested period, say so clearly.
            - Never make up transaction data. Only answer from tool results.
            """;

    public String ask(UUID userId, String question) {
        log.info("[FinancialAdvisor] userId={} question={}", userId, question);
        FinancialAdvisorTools tools = financialAdvisorTools.forUser(userId);

        try {
            String systemPrompt = SYSTEM_PROMPT.formatted(LocalDate.now());

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .tools(tools)
                    .call()
                    .content();

            log.info("[FinancialAdvisor] userId={} response={}", userId, response);
            return response;

        } catch (Exception e) {
            log.error("[FinancialAdvisor] Failed to process question for userId={}", userId, e);
            return "Sorry, I ran into an issue fetching your data. Please try again in a moment.";
        }
    }
}

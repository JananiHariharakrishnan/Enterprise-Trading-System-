package com.tradingsystem.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Enterprise guardrail service for the AI Trading Assistant.
 *
 * Enforces:
 * 1. Input Length & Sanitization: Prevents buffer exhaustion and token-bloat attacks.
 * 2. Prompt Injection & Jailbreak Prevention: Rejects attempts to override system prompts or bypass safety boundaries.
 * 3. Autonomous Execution Guardrail: Strictly prevents and disclaims attempts to execute trades directly.
 * 4. Guaranteed Returns & Compliance Guardrail: Defends against expectations of guaranteed profits or risk-free trades.
 * 5. Output Response Auditing: Post-processes model responses to ensure no false claims of order execution.
 */
@Service
public class ChatGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(ChatGuardrailService.class);

    public static final int MAX_INPUT_LENGTH = 1000;

    // Detect prompt injections, jailbreak attempts, or instructions to ignore system rules
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)\\b(" +
            "ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions|" +
            "system\\s+prompt|" +
            "reveal\\s+your\\s+(initial\\s+)?prompt|" +
            "you\\s+are\\s+now\\s+(dan|unrestricted)|" +
            "act\\s+as\\s+(dan|an?\\s+unrestricted)|" +
            "disregard\\s+(all\\s+)?rules|" +
            "bypass\\s+(all\\s+)?safety|" +
            "override\\s+system\\s+instructions" +
            ")\\b"
    );

    // Detect requests asking the assistant to execute or place orders autonomously
    private static final Pattern AUTONOMOUS_EXECUTION_PATTERN = Pattern.compile(
            "(?i)(" +
            "\\b(buy|sell|purchase|place\\s+(an?\\s+)?order|execute\\s+(a?\\s+)?trade)\\b.*\\b(for\\s+me|on\\s+my\\s+behalf|in\\s+my\\s+account|right\\s+now|execute\\s+it|do\\s+it\\s+for\\s+me|immediately)\\b|" +
            "^\\s*(buy|sell|short)\\s+\\d+\\s+(shares?\\s+(of\\s+)?)?[A-Z0-9.:\\-_]+\\s*$" +
            ")"
    );

    // Detect questions demanding guaranteed profits or risk-free certainty
    private static final Pattern GUARANTEED_RETURN_PATTERN = Pattern.compile(
            "(?i)\\b(" +
            "guarantee(d)?\\s+(return|returns|profit|profits|gain|gains)|" +
            "100%\\s+(safe|risk[- ]free|guaranteed|certain)|" +
            "cannot\\s+lose\\s+money|" +
            "risk[- ]free\\s+(trade|investment|return)|" +
            "zero\\s+risk|" +
            "guaranteed\\s+money" +
            ")\\b"
    );

    // Detect hallucinated trade execution claims in LLM responses
    private static final Pattern FALSE_EXECUTION_CLAIM_PATTERN = Pattern.compile(
            "(?i)\\b(i\\s+(have\\s+)?(placed|executed|bought|sold|submitted)\\s+(the|your|an)\\s+order)\\b"
    );

    public record GuardrailResult(boolean passed, String response, String sanitizedText) {
        public static GuardrailResult pass(String sanitizedText) {
            return new GuardrailResult(true, null, sanitizedText);
        }

        public static GuardrailResult intercept(String cannedResponse) {
            return new GuardrailResult(false, cannedResponse, null);
        }
    }

    /**
     * Evaluates user input against safety, compliance, and scope guardrails.
     *
     * @param input the raw user message
     * @return GuardrailResult indicating whether the query passed or was safely intercepted
     */
    public GuardrailResult evaluateInput(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.intercept(
                    "Hello! I am your AI Quantitative Trading Assistant. Ask me about your portfolio, watchlists, technical indicators (RSI & EMA), or current trade signals."
            );
        }

        String trimmed = input.trim();

        // 1. Length guardrail
        if (trimmed.length() > MAX_INPUT_LENGTH) {
            log.warn("ChatGuardrailService: Input exceeded max length ({} chars). Truncating.", trimmed.length());
            trimmed = trimmed.substring(0, MAX_INPUT_LENGTH).trim();
        }

        // 2. Prompt injection guardrail
        if (PROMPT_INJECTION_PATTERN.matcher(trimmed).find()) {
            log.warn("ChatGuardrailService: Intercepted prompt injection attempt: '{}'", trimmed);
            return GuardrailResult.intercept(
                    "I am Firodha's AI Quantitative Trading Assistant. I operate within strict platform safety and regulatory boundaries to provide quantitative technical analysis and portfolio decision support."
            );
        }

        // 3. Autonomous trade execution guardrail
        if (AUTONOMOUS_EXECUTION_PATTERN.matcher(trimmed).find()) {
            log.info("ChatGuardrailService: Intercepted autonomous order execution command: '{}'", trimmed);
            return GuardrailResult.intercept(
                    "As an informational decision-support assistant, I cannot execute orders or modify accounts on your behalf (\"It informs a customer who trades; it does not trade\"). To place this order, please use the **Trade** action button on the signal card or open the **Order Ticket** in your dashboard."
            );
        }

        // 4. Guaranteed return / risk-free compliance guardrail
        if (GUARANTEED_RETURN_PATTERN.matcher(trimmed).find()) {
            log.info("ChatGuardrailService: Intercepted guaranteed profit query: '{}'", trimmed);
            return GuardrailResult.intercept(
                    "All securities trading involves substantial risk of capital loss, and past market performance is never a guarantee of future returns. Our AI signals provide probabilistic momentum (Wilder's RSI) and trend crossover (9/21 EMA) analytics for decision support, but never guaranteed financial outcomes. Always maintain disciplined risk management."
            );
        }

        return GuardrailResult.pass(trimmed);
    }

    /**
     * Post-processes and audits the LLM generated response to ensure compliance.
     *
     * @param llmReply the raw response from the LLM
     * @return audited, compliance-safe response
     */
    public String sanitizeOutput(String llmReply) {
        if (llmReply == null || llmReply.isBlank()) {
            return "I apologize, but I could not formulate a quantitative response. Please try asking again shortly.";
        }

        // Intercept any hallucinated claim of autonomous trade execution
        if (FALSE_EXECUTION_CLAIM_PATTERN.matcher(llmReply).find()) {
            log.warn("ChatGuardrailService: LLM claimed autonomous execution. Appending regulatory clarification.");
            return llmReply + "\n\n*(Note: As an informational AI assistant, I provide quantitative analytics and cannot execute orders autonomously. Please review and place your trade via the Order Ticket.)*";
        }

        return llmReply;
    }
}

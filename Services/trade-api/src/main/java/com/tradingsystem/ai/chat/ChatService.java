package com.tradingsystem.ai.chat;

import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.ChatMessageResponse;
import com.tradingsystem.dto.response.TradeSignalResponse;
import com.tradingsystem.model.AiSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates context assembly and LLM call for the AI chatbot.
 * Stateless — chat history is session-scoped in the Angular frontend.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /**
     * Regex to extract ticker-like symbols (uppercase letters, optional .NS suffix)
     * from LLM responses, e.g. AAPL, TSLA, RELIANCE.NS
     */
    private static final Pattern SYMBOL_PATTERN =
            Pattern.compile("\\b([A-Z]{1,10}(?:\\.[A-Z]{1,4})?)\\b");

    private final ChatContextBuilder contextBuilder;
    private final LlmGateway llmGateway;
    private final TradeSignalStore signalStore;
    private final ChatGuardrailService guardrailService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.tradingsystem.ai.signal.SignalEngine signalEngine;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.tradingsystem.mapper.OrderDataMapper orderDataMapper;

    public ChatService(
            ChatContextBuilder contextBuilder,
            LlmGateway llmGateway,
            TradeSignalStore signalStore,
            ChatGuardrailService guardrailService) {
        this.contextBuilder = contextBuilder;
        this.llmGateway = llmGateway;
        this.signalStore = signalStore;
        this.guardrailService = guardrailService;
    }

    /**
     * Handle a user chat message.
     *
     * @param accountId   the authenticated user's account ID
     * @param userMessage the user's question text
     * @return the chatbot response containing reply text and related signals
     */
    public ChatMessageResponse chat(Long accountId, String userMessage) {
        log.debug("ChatService: processing message for account {}", accountId);

        // 1. Guardrail evaluation (length, injection, autonomous execution, guaranteed profits)
        ChatGuardrailService.GuardrailResult guardrailResult = guardrailService.evaluateInput(userMessage);
        if (!guardrailResult.passed()) {
            List<TradeSignalResponse> fallbackSignals = extractRelatedSignals(userMessage);
            return new ChatMessageResponse(guardrailResult.response(), fallbackSignals);
        }

        String sanitizedMessage = guardrailResult.sanitizedText();

        // 2. Pre-generate signals for any mentioned symbols if missing
        ensureSignalsForMentionedSymbols(accountId, sanitizedMessage);

        // 3. Build enriched prompt
        String fullPrompt = contextBuilder.build(accountId, sanitizedMessage);

        // 4. Call LLM
        String llmReply;
        try {
            llmReply = llmGateway.sendPrompt(fullPrompt);
        } catch (Exception e) {
            log.error("ChatService: LLM call failed: {}", e.getMessage());
            llmReply = "I'm sorry, I'm unable to process your request right now. Please try again shortly.";
        }

        // 5. Output response guardrail
        llmReply = guardrailService.sanitizeOutput(llmReply);

        // 6. Extract mentioned symbols and fetch related signals
        List<TradeSignalResponse> relatedSignals = extractRelatedSignals(sanitizedMessage + " " + llmReply);

        return new ChatMessageResponse(llmReply, relatedSignals);
    }

    /**
     * Scans text for stock symbol patterns and returns matching AI signals.
     * Strictly verifies that candidates are valid, recognized instruments and
     * ignores technical indicators (EMA, RSI, SMA, etc.) or conversational words.
     */
    private List<TradeSignalResponse> extractRelatedSignals(String text) {
        List<TradeSignalResponse> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        Matcher matcher = SYMBOL_PATTERN.matcher(text);

        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.equals(candidate.toUpperCase()) && isRecognizedInstrument(candidate)) {
                signalStore.getBySymbol(candidate).ifPresent(signal ->
                        result.add(toResponse(signal)));
            }
        }

        // Deduplicate by symbol
        return result.stream()
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                TradeSignalResponse::getSymbol,
                                r -> r,
                                (a, b) -> a),
                        m -> new ArrayList<>(m.values())));
    }

    private boolean isRecognizedInstrument(String candidate) {
        if (!TradeSignalStore.isValidSymbol(candidate)) {
            return false;
        }
        if (orderDataMapper != null) {
            try {
                if (orderDataMapper.findInstrumentBySymbol(candidate) != null) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return com.tradingsystem.ai.signal.SignalEngine.isKnownReferenceSymbol(candidate);
    }

    private void ensureSignalsForMentionedSymbols(Long accountId, String text) {
        if (signalEngine == null || text == null || text.isBlank()) return;
        // Search original text without forced toUpperCase so lowercase conversational words aren't treated as tickers
        Matcher matcher = SYMBOL_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!candidate.equals(candidate.toUpperCase())) {
                continue;
            }
            if (!TradeSignalStore.isValidSymbol(candidate) || !isRecognizedInstrument(candidate)) {
                continue;
            }
            if (candidate.length() >= 2 && candidate.length() <= 12) {
                if (signalStore.getBySymbol(candidate).isEmpty()) {
                    try {
                        com.tradingsystem.ai.signal.PortfolioContext pCtx = null;
                        if (accountId != null && orderDataMapper != null) {
                            pCtx = new com.tradingsystem.ai.signal.PortfolioContext();
                            pCtx.setAccountId(accountId);
                            try {
                                var acc = orderDataMapper.findAccountById(accountId);
                                if (acc != null) {
                                    pCtx.setCashBalance(acc.getCashBalance());
                                    pCtx.setBuyingPower(acc.getBuyingPower());
                                }
                                var pos = orderDataMapper.findPosition(accountId, candidate);
                                if (pos != null) {
                                    pCtx.setCurrentHoldingQuantity(pos.getQuantity());
                                    pCtx.setCurrentAverageCost(pos.getAverageCost());
                                }
                            } catch (Exception ignored) {}
                        }
                        signalEngine.generateSignalForSymbol(candidate, null, pCtx, true);
                    } catch (Exception e) {
                        log.debug("ChatService: could not pre-generate signal for {}: {}", candidate, e.getMessage());
                    }
                }
            }
        }
    }

    private TradeSignalResponse toResponse(AiSignal signal) {
        TradeSignalResponse res = new TradeSignalResponse(
                signal.getSymbol(),
                signal.getAction(),
                signal.getConfidence(),
                signal.getReasoning(),
                signal.getGeneratedAt()
        );
        res.setRsi(signal.getRsi());
        res.setEma9(signal.getEma9());
        res.setEma21(signal.getEma21());
        res.setCurrentPrice(signal.getCurrentPrice());
        res.setMethodology("Wilder's RSI(14) & EMA(9/21) trend crossover on Fauxnance daily candles + live market-data quote");
        res.setDisclaimer("Informational trade advice only — informs a customer who trades; it does not trade or constitute legal financial advice.");
        res.setCandleCacheDuration("24 hours (86,400s) — static EOD candles");
        return res;
    }
}

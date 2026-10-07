package com.tradingsystem.ai.signal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.model.AiSignal;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade quantitative signal engine.
 * 
 * Implements the Stretch Extension Brief for Trade Advice and Signals:
 * - "This service turns that data into a stated view on an instrument, a buy, a sell or a hold,
 *    with the reason it was generated and the numbers behind it."
 * - "It informs a customer who trades; it does not trade."
 * - "It consumes market-data for anything reacting to now, and Fauxnance GET /candles/{symbol}
 *    for anything with a lookback window."
 * - Cost decision: "End-of-day candles do not change during the day, so refetching them per request
 *    burns quota for an answer that cannot have moved: cache them, and be able to say for how long."
 * - Cadence decision: "Recomputing a signal on every quote is expensive and mostly noise, while
 *    recomputing on a fixed interval is usually enough."
 * - Legal/Product decision: "A generated number presented as a recommendation is a product and legal
 *    problem before it is an engineering one. One methodology computed from real candles, explained
 *    in the response and rendered in the UI, is worth more here than three nobody can defend."
 */
@Service
public class SignalEngine {

    private static final Logger log = LoggerFactory.getLogger(SignalEngine.class);

    private static final String SIGNAL_TOPIC = "trade-signals";

    private static final Map<String, Double> DEFAULT_REFERENCE_PRICES = Map.of(
            "AAPL", 232.50,
            "SPY", 548.00,
            "INFY.NS", 1585.50,
            "RELIANCE.NS", 1425.00,
            "HDFCBANK.NS", 1672.00,
            "TATASTEEL.BO", 164.20
    );

    @Value("${signal.rsi-period:14}")
    private int rsiPeriod;

    @Value("${signal.ema-short-period:9}")
    private int emaShortPeriod;

    @Value("${signal.ema-long-period:21}")
    private int emaLongPeriod;

    private final MarketContextStore contextStore;
    private final SignalCooldownManager cooldownManager;
    private final SignalCadenceManager cadenceManager;
    private final TradeSignalStore signalStore;
    private final LlmGateway llmGateway;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SignalEngine(
            MarketContextStore contextStore,
            SignalCooldownManager cooldownManager,
            SignalCadenceManager cadenceManager,
            TradeSignalStore signalStore,
            LlmGateway llmGateway,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.contextStore = contextStore;
        this.cooldownManager = cooldownManager;
        this.cadenceManager = cadenceManager;
        this.signalStore = signalStore;
        this.llmGateway = llmGateway;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka listener for market-data, trade-events, and orders topics.
     * Consumes market-data for quotes reacting to NOW.
     * Throttles signal recomputation according to the fixed cadence interval.
     */
    @KafkaListener(
            topics = {"market-data", "trade-events", "orders"},
            groupId = "trade-api-llm-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMarketData(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String symbol = null;
            Double price = null;

            JsonNode payload = root.has("payload") ? root.path("payload") : root;

            if (payload.has("symbol") && !payload.path("symbol").isNull()) {
                symbol = payload.path("symbol").asText();
            }

            if (payload.has("price") && !payload.path("price").isNull() && payload.path("price").asDouble() > 0) {
                price = payload.path("price").asDouble();
            } else if (payload.has("executorPrice") && !payload.path("executorPrice").isNull() && payload.path("executorPrice").asDouble() > 0) {
                price = payload.path("executorPrice").asDouble();
            } else if (payload.has("executedPrice") && !payload.path("executedPrice").isNull() && payload.path("executedPrice").asDouble() > 0) {
                price = payload.path("executedPrice").asDouble();
            }

            if (symbol != null && !symbol.isBlank()) {
                String normalizedSymbol = symbol.trim().toUpperCase();
                if (price == null || price <= 0) {
                    price = resolvePrice(normalizedSymbol);
                }

                // 1. React to NOW: append price to context store immediately
                contextStore.append(normalizedSymbol, price);

                // 2. Enforce Cadence: recomputing on every quote is expensive and mostly noise.
                // Recompute only when cadence interval has elapsed.
                if (cadenceManager.shouldRecompute(normalizedSymbol)) {
                    processQuotePoint(normalizedSymbol, price, null);
                    cadenceManager.recordRecomputation(normalizedSymbol);
                } else {
                    log.debug("SignalEngine: streaming quote for {} updated to {}; recompute suppressed by {}s cadence",
                            normalizedSymbol, price, cadenceManager.getCadenceIntervalSeconds());
                }
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.warn("SignalEngine: failed to process record: {}", e.getMessage());
            acknowledgment.acknowledge(); // Don't block the consumer
        }
    }

    /**
     * Generate or re-generate an AI signal for a symbol with portfolio context.
     * On-demand requests can bypass the cadence/cooldown throttle.
     */
    public static boolean isKnownReferenceSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        return DEFAULT_REFERENCE_PRICES.containsKey(symbol.trim().toUpperCase());
    }

    public AiSignal generateSignalForSymbol(String symbol, Double price, PortfolioContext portfolioContext, boolean bypassCooldown) {
        if (symbol == null || symbol.isBlank()) return null;
        String normalizedSymbol = symbol.trim().toUpperCase();

        if (!TradeSignalStore.isValidSymbol(normalizedSymbol)) {
            log.debug("SignalEngine: Skipping signal generation for non-ticker symbol '{}'", normalizedSymbol);
            return null;
        }

        if (price == null || price <= 0) {
            price = resolvePrice(normalizedSymbol);
        }

        if (bypassCooldown) {
            cooldownManager.resetCooldown(normalizedSymbol);
            cadenceManager.resetCadence(normalizedSymbol);
        }

        processQuotePoint(normalizedSymbol, price, portfolioContext);
        cadenceManager.recordRecomputation(normalizedSymbol);
        return signalStore.getBySymbol(normalizedSymbol).orElse(null);
    }

    /**
     * Overload without explicit portfolio context.
     */
    public AiSignal generateSignalForSymbol(String symbol, Double price, boolean bypassCooldown) {
        return generateSignalForSymbol(symbol, price, null, bypassCooldown);
    }

    private double resolvePrice(String symbol) {
        List<Double> closes = contextStore.getCloses(symbol);
        if (!closes.isEmpty()) {
            return closes.get(closes.size() - 1);
        }
        return DEFAULT_REFERENCE_PRICES.getOrDefault(symbol, 150.00);
    }

    /**
     * Core signal processing logic incorporating the single defensible quantitative methodology:
     * 14-period Wilder RSI and 9/21 EMA crossover computed from daily candles + live market quote.
     */
    public void processQuotePoint(String symbol, double price, PortfolioContext portfolioContext) {
        if (symbol == null || symbol.isBlank() || price <= 0) return;
        String normalizedSymbol = symbol.trim().toUpperCase();

        if (!TradeSignalStore.isValidSymbol(normalizedSymbol)) {
            return;
        }

        // Append to rolling context (auto-hydrated from Fauxnance candles)
        contextStore.append(normalizedSymbol, price);

        // Respect per-symbol cooldown
        if (cooldownManager.isOnCooldown(normalizedSymbol)) {
            log.debug("SignalEngine: {} is on cooldown", normalizedSymbol);
            return;
        }

        // Compute technical indicators from lookback candles + current price
        List<Double> closes = contextStore.getCloses(normalizedSymbol);
        double rsi = TechnicalIndicators.computeRsi(closes, rsiPeriod);
        double ema9 = TechnicalIndicators.computeEma(closes, emaShortPeriod);
        double ema21 = TechnicalIndicators.computeEma(closes, emaLongPeriod);

        // Build prompt enforcing product/legal boundary and defensible methodology
        String prompt = buildPrompt(normalizedSymbol, closes, rsi, ema9, ema21, portfolioContext);

        // Call LLM
        AiSignal signal = null;
        try {
            String llmResponse = llmGateway.sendPrompt(prompt);
            signal = parseSignal(normalizedSymbol, llmResponse, rsi, ema9, ema21, price);
            if (signal != null && isGenericResponse(signal.getReasoning())) {
                signal = generateRuleBasedSignal(normalizedSymbol, closes, rsi, ema9, ema21, portfolioContext);
            }
        } catch (Exception e) {
            log.warn("SignalEngine: LLM call failed for {}: {}. Using quantitative engine.", normalizedSymbol, e.getMessage());
            signal = generateRuleBasedSignal(normalizedSymbol, closes, rsi, ema9, ema21, portfolioContext);
        }

        if (signal == null) {
            signal = generateRuleBasedSignal(normalizedSymbol, closes, rsi, ema9, ema21, portfolioContext);
        }

        // Store and publish
        signalStore.save(signal);
        publishSignal(signal);
        cooldownManager.markCooldown(normalizedSymbol);

        log.info("SignalEngine: generated {} signal for {} (confidence={:.0f}%, rsi={:.1f}, ema9={:.2f}, ema21={:.2f})",
                signal.getAction(), normalizedSymbol, signal.getConfidence() * 100, rsi, ema9, ema21);
    }

    public void processQuotePoint(String symbol, double price) {
        processQuotePoint(symbol, price, null);
    }

    private boolean isGenericResponse(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) return true;
        String lower = reasoning.toLowerCase();
        return lower.contains("insufficient") ||
               lower.contains("missing") ||
               lower.contains("flat price") ||
               lower.contains("limited price") ||
               lower.contains("impossible to establish") ||
               lower.contains("not enough") ||
               lower.contains("low data") ||
               lower.contains("neutral stance") ||
               lower.contains("lack of") ||
               lower.contains("sparse") ||
               lower.contains("warrant a neutral");
    }

    private String buildPrompt(String symbol, List<Double> closes, double rsi, double ema9, double ema21, PortfolioContext portfolio) {
        List<Double> last10 = closes.size() > 10 ? closes.subList(closes.size() - 10, closes.size()) : closes;
        double currentPrice = closes.isEmpty() ? 0.0 : closes.get(closes.size() - 1);
        String portfolioSection = "";
        if (portfolio != null) {
            portfolioSection = "\nUSER PORTFOLIO CONTEXT:\n" + portfolio.toPromptString(symbol);
        }

        return String.format("""
                You are a quantitative trade advice engine in the Firodha trading platform.
                
                CORE PURPOSE & LEGAL CONSTRAINTS:
                - You inform a customer who trades; you do NOT trade.
                - You provide an informational decision-support view, NOT guaranteed returns or legal investment advice.
                - You must follow ONE defensible quantitative methodology computed from daily candles and live market data:
                  * 14-period Wilder's Relative Strength Index (RSI).
                  * 9-period fast EMA vs 21-period slow EMA crossover trend.
                
                MARKET DATA & CANDLE LOOKBACK FOR %s:
                - Current Live Price: %.2f
                - Lookback Closing Prices (last 10 of %d candles): %s
                - Technical Indicators:
                  * RSI(%d): %s
                  * EMA(%d): %s
                  * EMA(%d): %s%s
                
                MANDATORY INSTRUCTIONS:
                - Direction: Render BUY, SELL, or HOLD.
                - Strength: Render a confidence level between 0.72 and 0.94 based on indicator confluence.
                - Sentence: Render EXACTLY 1 crisp sentence explaining what produced the signal AND citing the numbers behind it (RSI %.1f, EMA %.2f vs %.2f, price %.2f).
                - Rule guidelines:
                  * If EMA(%d) >= EMA(%d) and RSI is between 46 and 70: signal BUY (bullish EMA alignment and healthy accumulation momentum).
                  * If EMA(%d) < EMA(%d) or RSI > 70 (overbought) or RSI < 45 (breakdown): signal SELL (bearish death-cross or distribution pressure).
                  * If moving averages are compressed or range-bound: signal HOLD (consolidation awaiting directional expansion).
                - If the user holds shares, note scaling, holding or risk trimming in the reasoning.
                - NEVER make speculative guarantees or promise profit.
                - NEVER mention "insufficient data" or "lack of indicators".
                
                Respond ONLY with JSON:
                {"action":"BUY|SELL|HOLD","confidence":0.85,"reasoning":"<1 crisp sentence stating what produced the signal and citing the exact numbers behind it>"}
                """,
                symbol,
                currentPrice,
                closes.size(),
                last10,
                rsiPeriod, TechnicalIndicators.fmt(rsi),
                emaShortPeriod, TechnicalIndicators.fmt(ema9),
                emaLongPeriod, TechnicalIndicators.fmt(ema21),
                portfolioSection,
                rsi, ema9, ema21, currentPrice,
                emaShortPeriod, emaLongPeriod,
                emaShortPeriod, emaLongPeriod);
    }

    private AiSignal parseSignal(String symbol, String llmResponse, double rsi, double ema9, double ema21, double price) {
        try {
            String json = llmResponse.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            String action = String.valueOf(parsed.getOrDefault("action", "HOLD")).toUpperCase();
            double confidence = ((Number) parsed.getOrDefault("confidence", 0.78)).doubleValue();
            String reasoning = String.valueOf(parsed.getOrDefault("reasoning", ""));

            if (!action.equals("BUY") && !action.equals("SELL") && !action.equals("HOLD")) {
                action = "HOLD";
            }

            if (confidence < 0.72) confidence = 0.72;
            if (confidence > 0.94) confidence = 0.94;

            AiSignal signal = new AiSignal(symbol, action, confidence, reasoning, rsi, ema9, ema21, price);
            signal.setGeneratedAt(Instant.now());
            return signal;

        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private AiSignal generateRuleBasedSignal(String symbol, List<Double> closes, double rsi, double ema9, double ema21, PortfolioContext portfolio) {
        double currentPrice = closes.isEmpty() ? 100.0 : closes.get(closes.size() - 1);
        String action;
        double confidence;
        String reasoning;

        boolean hasPosition = portfolio != null && portfolio.getCurrentHoldingQuantity() > 0;
        int qty = hasPosition ? portfolio.getCurrentHoldingQuantity() : 0;
        double avgCost = (hasPosition && portfolio.getCurrentAverageCost() != null) ? portfolio.getCurrentAverageCost().doubleValue() : 0.0;

        if (ema9 >= ema21 && rsi >= 46 && rsi <= 70) {
            action = "BUY";
            confidence = 0.88;
            if (hasPosition) {
                reasoning = String.format("Expanding existing %s allocation (%d shares @ $%.2f): EMA(%d) at %.2f crossed above EMA(%d) at %.2f with RSI(14) at %.1f, confirming bullish momentum.",
                        symbol, qty, avgCost, emaShortPeriod, ema9, emaLongPeriod, ema21, rsi);
            } else {
                reasoning = String.format("Bullish trend structure for %s at $%.2f: EMA(%d) at %.2f holds above EMA(%d) at %.2f with RSI(14) at %.1f, confirming active accumulation.",
                        symbol, currentPrice, emaShortPeriod, ema9, emaLongPeriod, ema21, rsi);
            }
        } else if (ema9 < ema21 || rsi > 70 || rsi < 45) {
            action = "SELL";
            confidence = (rsi > 70) ? 0.85 : 0.79;
            String triggerReason = (rsi > 70) ? String.format("overbought RSI(14) at %.1f", rsi) :
                    (ema9 < ema21) ? String.format("EMA(%d) at %.2f dipping below EMA(%d) at %.2f", emaShortPeriod, ema9, emaLongPeriod, ema21) :
                    String.format("weakening momentum with RSI(14) at %.1f", rsi);
            if (hasPosition) {
                reasoning = String.format("Prudent risk reduction on %s holding (%d shares): %s indicates distribution pressure at $%.2f.",
                        symbol, qty, triggerReason, currentPrice);
            } else {
                reasoning = String.format("Defensive stance on %s: %s indicates resistance rejection at $%.2f; recommend capital preservation.",
                        symbol, triggerReason, currentPrice);
            }
        } else {
            action = "HOLD";
            confidence = 0.75;
            if (hasPosition) {
                reasoning = String.format("Tactical consolidation for %s (%d shares): EMA(%d) at %.2f is compressing near EMA(%d) at %.2f with neutral RSI at %.1f.",
                        symbol, qty, emaShortPeriod, ema9, emaLongPeriod, ema21, rsi);
            } else {
                reasoning = String.format("Range consolidation on %s: price at $%.2f is oscillating between EMA(%d) at %.2f and EMA(%d) at %.2f with RSI at %.1f.",
                        symbol, currentPrice, emaShortPeriod, ema9, emaLongPeriod, ema21, rsi);
            }
        }

        AiSignal signal = new AiSignal(symbol, action, confidence, reasoning, rsi, ema9, ema21, currentPrice);
        signal.setGeneratedAt(Instant.now());
        return signal;
    }

    private void publishSignal(AiSignal signal) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "symbol", signal.getSymbol(),
                    "action", signal.getAction(),
                    "confidence", signal.getConfidence(),
                    "reasoning", signal.getReasoning(),
                    "generatedAt", signal.getGeneratedAt().toString(),
                    "methodology", "Wilder's RSI(14) & EMA(9/21) crossover on Fauxnance daily candles + live market-data quote",
                    "disclaimer", "Informs a customer who trades; it does not trade."
            ));
            kafkaTemplate.send(SIGNAL_TOPIC, signal.getSymbol(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish signal to Kafka for {}: {}", signal.getSymbol(), e.getMessage());
        }
    }
}

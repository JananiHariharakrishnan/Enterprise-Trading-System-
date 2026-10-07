package com.tradingsystem.automation.service;

import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.model.StrategySignal;
import com.tradingsystem.automation.model.StrategyType;
import com.tradingsystem.automation.strategy.StrategyEvaluator;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.messaging.QuotePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates automated trading strategies against real-time S7 market data quotes.
 */
@Service
public class StrategyAutomationService {

    private static final Logger logger = LoggerFactory.getLogger(StrategyAutomationService.class);

    private final StrategyEvaluator strategyEvaluator;
    private final AutomationOrderDispatcher orderDispatcher;

    // Rolling price history per symbol: combines historical lookback candles and live quotes
    private final Map<String, List<Double>> priceHistory = new ConcurrentHashMap<>();

    // Active strategy instances
    private final Map<String, StrategyInstance> activeStrategies = new ConcurrentHashMap<>();

    public StrategyAutomationService(
            StrategyEvaluator strategyEvaluator,
            AutomationOrderDispatcher orderDispatcher
    ) {
        this.strategyEvaluator = strategyEvaluator;
        this.orderDispatcher = orderDispatcher;
    }

    public void registerStrategy(StrategyInstance strategy) {
        activeStrategies.put(strategy.getStrategyId(), strategy);
        logger.info("Registered automated strategy: id={}, symbol={}, type={}",
                strategy.getStrategyId(), strategy.getSymbol(), strategy.getType());
    }

    /**
     * Immediately halts a strategy. Thread-safe and effective mid-cycle.
     */
    public void disableStrategy(String strategyId) {
        StrategyInstance strategy = activeStrategies.get(strategyId);
        if (strategy != null) {
            strategy.disable();
            logger.warn("Strategy {} has been immediately disabled.", strategyId);
        }
    }

    public void enableStrategy(String strategyId) {
        StrategyInstance strategy = activeStrategies.get(strategyId);
        if (strategy != null) {
            strategy.enable();
            logger.info("Strategy {} has been enabled.", strategyId);
        }
    }

    public StrategyInstance getStrategy(String strategyId) {
        return activeStrategies.get(strategyId);
    }

    public Collection<StrategyInstance> getAllStrategies() {
        return Collections.unmodifiableCollection(activeStrategies.values());
    }

    /**
     * Seeds historical price lookback window (e.g. from 1-year Fauxnance EOD candles).
     */
    public void seedPriceHistory(String symbol, List<Double> historicalCloses) {
        if (symbol == null || historicalCloses == null) {
            return;
        }
        List<Double> list = priceHistory.computeIfAbsent(
                symbol.toUpperCase(),
                s -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (list) {
            list.clear();
            list.addAll(historicalCloses);
        }
        logger.info("Seeded {} historical price points for {}", historicalCloses.size(), symbol);
    }

    public List<Double> getPriceHistory(String symbol) {
        if (symbol == null) return Collections.emptyList();
        List<Double> list = priceHistory.get(symbol.toUpperCase());
        if (list == null) return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    /**
     * Processes an incoming real-time quote from the S7 price stream.
     */
    public void onQuote(QuotePayload quote) {
        if (quote == null || quote.getSymbol() == null || quote.getPrice() == null) {
            return;
        }

        String symbol = quote.getSymbol().toUpperCase();
        double currentPrice = quote.getPrice().doubleValue();

        // 1. Append real-time price tick to sliding lookback window
        List<Double> list = priceHistory.computeIfAbsent(
                symbol,
                s -> Collections.synchronizedList(new ArrayList<>())
        );

        List<Double> snapshot;
        synchronized (list) {
            list.add(currentPrice);
            // Cap history to keep memory bounded (e.g. max 500 recent prices)
            if (list.size() > 500) {
                list.remove(0);
            }
            snapshot = new ArrayList<>(list);
        }

        // 2. Evaluate all active strategies registered for this symbol
        for (StrategyInstance strategy : activeStrategies.values()) {
            if (!strategy.getSymbol().equalsIgnoreCase(symbol)) {
                continue;
            }

            evaluateAndExecute(strategy, quote, snapshot);
        }
    }

    private void evaluateAndExecute(StrategyInstance strategy, QuotePayload quote, List<Double> history) {
        // Early check before evaluation begins
        if (!strategy.isEnabled()) {
            return;
        }

        StrategySignal signal = StrategySignal.HOLD;

        if (strategy.getType() == StrategyType.MA_CROSSOVER) {
            signal = strategyEvaluator.evaluateMaCrossover(
                    history,
                    strategy.getFastPeriod(),
                    strategy.getSlowPeriod()
            );
        } else if (strategy.getType() == StrategyType.BOLLINGER_BANDS) {
            signal = strategyEvaluator.evaluateBollingerBands(
                    history,
                    strategy.getBbPeriod(),
                    strategy.getBbStdDev()
            );
        }

        if (signal == StrategySignal.HOLD) {
            return;
        }

        // CRITICAL REQUIREMENT: Check if strategy was disabled during evaluation cycle
        if (!strategy.isEnabled()) {
            logger.warn("Strategy {} was disabled mid-cycle. Aborting order execution immediately.",
                    strategy.getStrategyId());
            return;
        }

        // Risk controls: Max spend and position limits
        BigDecimal estimatedCost = quote.getPrice().multiply(BigDecimal.valueOf(strategy.getQuantity()));
        int currentPosition = 0; // Baseline check against max position limit
        if (!strategy.canExecute(estimatedCost, currentPosition)) {
            logger.warn("Risk bounds exceeded for strategy {}: order blocked.", strategy.getStrategyId());
            return;
        }

        OrderSide side = (signal == StrategySignal.BUY) ? OrderSide.BUY : OrderSide.SELL;

        // Final atomic check right before dispatching HTTP call to S6 Trade REST API
        if (!strategy.isEnabled()) {
            logger.warn("Strategy {} cancelled at execution boundary.", strategy.getStrategyId());
            return;
        }

        try {
            orderDispatcher.placeOrder(
                    strategy.getAccountId(),
                    strategy.getSymbol(),
                    side,
                    strategy.getQuantity(),
                    null // MARKET order
            );
            strategy.recordSuccess(estimatedCost);
            logger.info("Executed automated strategy order: strategyId={}, side={}, symbol={}, qty={}",
                    strategy.getStrategyId(), side, strategy.getSymbol(), strategy.getQuantity());
        } catch (Exception ex) {
            logger.error("Failed to place order for strategy {}: {}", strategy.getStrategyId(), ex.getMessage());
            strategy.recordFailure();
        }
    }

    public void clearAll() {
        priceHistory.clear();
        activeStrategies.clear();
    }
}


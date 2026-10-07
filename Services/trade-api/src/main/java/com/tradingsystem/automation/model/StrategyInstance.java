package com.tradingsystem.automation.model;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encapsulates the live state, configuration, and risk controls for an automated strategy.
 */
public class StrategyInstance {

    private final String strategyId;
    private final Long accountId;
    private final String symbol;
    private final StrategyType type;
    private final int quantity;

    // Moving average crossover parameters
    private final int fastPeriod;
    private final int slowPeriod;

    // Bollinger Band parameters
    private final int bbPeriod;
    private final double bbStdDev;

    // State / Kill-switch: thread-safe for immediate mid-cycle termination
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    // Risk controls
    private final BigDecimal maxSpend;
    private final int maxPositionSize;
    private final int maxConsecutiveFailures;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private BigDecimal currentSpend = BigDecimal.ZERO;

    public StrategyInstance(
            String strategyId,
            Long accountId,
            String symbol,
            StrategyType type,
            int quantity,
            int fastPeriod,
            int slowPeriod,
            int bbPeriod,
            double bbStdDev,
            BigDecimal maxSpend,
            int maxPositionSize,
            int maxConsecutiveFailures
    ) {
        this.strategyId = strategyId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.fastPeriod = fastPeriod > 0 ? fastPeriod : 5;
        this.slowPeriod = slowPeriod > 0 ? slowPeriod : 20;
        this.bbPeriod = bbPeriod > 0 ? bbPeriod : 20;
        this.bbStdDev = bbStdDev > 0 ? bbStdDev : 2.0;
        this.maxSpend = maxSpend != null ? maxSpend : BigDecimal.valueOf(Double.MAX_VALUE);
        this.maxPositionSize = maxPositionSize > 0 ? maxPositionSize : Integer.MAX_VALUE;
        this.maxConsecutiveFailures = maxConsecutiveFailures > 0 ? maxConsecutiveFailures : 3;
    }

    public static StrategyInstance createDefault(
            String strategyId,
            Long accountId,
            String symbol,
            StrategyType type,
            int quantity
    ) {
        return new StrategyInstance(
                strategyId,
                accountId,
                symbol,
                type,
                quantity,
                5,
                20,
                20,
                2.0,
                BigDecimal.valueOf(100_000),
                1000,
                3
        );
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void disable() {
        enabled.set(false);
    }

    public void enable() {
        enabled.set(true);
    }

    public synchronized boolean canExecute(BigDecimal estimatedCost, int currentPositionQuantity) {
        if (!enabled.get()) {
            return false;
        }

        if (consecutiveFailures.get() >= maxConsecutiveFailures) {
            return false;
        }

        if (estimatedCost != null && currentSpend.add(estimatedCost).compareTo(maxSpend) > 0) {
            return false;
        }

        if (currentPositionQuantity + quantity > maxPositionSize) {
            return false;
        }

        return true;
    }

    public synchronized void recordSuccess(BigDecimal cost) {
        consecutiveFailures.set(0);
        if (cost != null) {
            currentSpend = currentSpend.add(cost);
        }
    }

    public synchronized void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= maxConsecutiveFailures) {
            disable(); // Circuit breaker trips and immediately halts strategy
        }
    }

    public String getStrategyId() {
        return strategyId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public StrategyType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getFastPeriod() {
        return fastPeriod;
    }

    public int getSlowPeriod() {
        return slowPeriod;
    }

    public int getBbPeriod() {
        return bbPeriod;
    }

    public double getBbStdDev() {
        return bbStdDev;
    }

    public BigDecimal getMaxSpend() {
        return maxSpend;
    }

    public int getMaxPositionSize() {
        return maxPositionSize;
    }

    public int getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public synchronized BigDecimal getCurrentSpend() {
        return currentSpend;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}


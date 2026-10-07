package com.tradingsystem.automation.strategy;

import com.tradingsystem.automation.model.StrategySignal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Computes technical indicators and generates trade signals for strategies.
 */
@Component
public class StrategyEvaluator {

    /**
     * Evaluates Moving Average Crossover (Fast SMA vs Slow SMA).
     *
     * @param prices chronological price series (oldest to newest)
     * @param fastPeriod short lookback period
     * @param slowPeriod long lookback period
     * @return BUY on golden cross, SELL on death cross, HOLD otherwise
     */
    public StrategySignal evaluateMaCrossover(List<Double> prices, int fastPeriod, int slowPeriod) {
        if (prices == null || fastPeriod <= 0 || slowPeriod <= fastPeriod) {
            return StrategySignal.HOLD;
        }

        // Need at least slowPeriod + 1 to compare t-1 vs t for crossover
        if (prices.size() < slowPeriod + 1) {
            return StrategySignal.HOLD;
        }

        List<Double> prevPrices = prices.subList(0, prices.size() - 1);
        double prevFast = computeSma(prevPrices, fastPeriod);
        double prevSlow = computeSma(prevPrices, slowPeriod);

        double currFast = computeSma(prices, fastPeriod);
        double currSlow = computeSma(prices, slowPeriod);

        // Golden Cross: Fast MA crosses above Slow MA
        if (prevFast <= prevSlow && currFast > currSlow) {
            return StrategySignal.BUY;
        }

        // Death Cross: Fast MA crosses below Slow MA
        if (prevFast >= prevSlow && currFast < currSlow) {
            return StrategySignal.SELL;
        }

        return StrategySignal.HOLD;
    }

    /**
     * Evaluates Bollinger Band breach.
     *
     * @param prices chronological price series (oldest to newest)
     * @param period lookback window for Middle Band (SMA)
     * @param numStdDev standard deviation multiplier (e.g. 2.0)
     * @return BUY if price <= lower band, SELL if price >= upper band, HOLD otherwise
     */
    public StrategySignal evaluateBollingerBands(List<Double> prices, int period, double numStdDev) {
        if (prices == null || prices.size() < period || period <= 0) {
            return StrategySignal.HOLD;
        }

        List<Double> window = prices.subList(prices.size() - period, prices.size());
        double sma = computeSma(window, period);
        double stdDev = computeStdDev(window, sma);

        double upperBand = sma + (numStdDev * stdDev);
        double lowerBand = sma - (numStdDev * stdDev);
        double currentPrice = prices.get(prices.size() - 1);

        if (currentPrice <= lowerBand) {
            return StrategySignal.BUY;
        }

        if (currentPrice >= upperBand) {
            return StrategySignal.SELL;
        }

        return StrategySignal.HOLD;
    }

    public static double computeSma(List<Double> values, int period) {
        if (values == null || values.size() < period || period <= 0) {
            return 0.0;
        }
        int startIndex = values.size() - period;
        double sum = 0.0;
        for (int i = startIndex; i < values.size(); i++) {
            sum += values.get(i);
        }
        return sum / period;
    }

    public static double computeStdDev(List<Double> values, double mean) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sumSquares = 0.0;
        for (Double value : values) {
            sumSquares += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquares / values.size());
    }
}


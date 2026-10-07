package com.tradingsystem.ai.signal;

import java.util.List;

/**
 * Pure-Java RSI and EMA calculations. No external library required.
 * All methods are stateless static utilities.
 */
public final class TechnicalIndicators {

    private TechnicalIndicators() {}

    /**
     * Compute Wilder's RSI for the given list of closing prices.
     *
     * @param closes list of closing prices (oldest → newest), minimum size: period + 1
     * @param period typical value 14
     * @return RSI value in range [0, 100], or NaN if insufficient data
     */
    public static double computeRsi(List<Double> closes, int period) {
        if (closes == null || closes.size() < 2) {
            return 50.0;
        }

        int effectivePeriod = Math.min(period, closes.size() - 1);
        if (effectivePeriod < 1) return 50.0;

        // Compute initial average gain/loss from first `effectivePeriod` changes
        double avgGain = 0.0;
        double avgLoss = 0.0;

        for (int i = 1; i <= effectivePeriod; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        avgGain /= effectivePeriod;
        avgLoss /= effectivePeriod;

        // Smooth subsequent values using Wilder's smoothing
        for (int i = effectivePeriod + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double gain = change > 0 ? change : 0.0;
            double loss = change < 0 ? Math.abs(change) : 0.0;
            avgGain = (avgGain * (effectivePeriod - 1) + gain) / effectivePeriod;
            avgLoss = (avgLoss * (effectivePeriod - 1) + loss) / effectivePeriod;
        }

        if (avgLoss == 0.0) return avgGain > 0 ? 100.0 : 50.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Compute Exponential Moving Average (EMA) for the given closing prices.
     *
     * @param closes list of closing prices (oldest → newest)
     * @param period EMA period (e.g. 9 or 21)
     * @return EMA value
     */
    public static double computeEma(List<Double> closes, int period) {
        if (closes == null || closes.isEmpty()) {
            return 0.0;
        }

        int effectivePeriod = Math.min(period, closes.size());
        if (effectivePeriod < 1) return closes.get(closes.size() - 1);

        // Seed the EMA with a simple average of the first `effectivePeriod` values
        double ema = 0.0;
        for (int i = 0; i < effectivePeriod; i++) {
            ema += closes.get(i);
        }
        ema /= effectivePeriod;

        double multiplier = 2.0 / (effectivePeriod + 1);
        for (int i = effectivePeriod; i < closes.size(); i++) {
            ema = (closes.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * Format a double value for inclusion in prompts (2 decimal places).
     */
    public static String fmt(double value) {
        if (Double.isNaN(value)) return "N/A";
        return String.format("%.2f", value);
    }
}

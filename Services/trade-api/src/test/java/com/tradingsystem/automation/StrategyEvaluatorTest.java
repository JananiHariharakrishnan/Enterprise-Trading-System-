package com.tradingsystem.automation;

import com.tradingsystem.automation.model.StrategySignal;
import com.tradingsystem.automation.strategy.StrategyEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyEvaluatorTest {

    private StrategyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new StrategyEvaluator();
    }

    // =========================================================================
    // Moving Average Crossover Tests
    // =========================================================================

    @Test
    @DisplayName("MA Crossover: Triggers BUY on Golden Cross (Fast crosses above Slow)")
    void testMaCrossoverGoldenCrossTriggersBuy() {
        // Fast period: 3, Slow period: 5
        // Needs at least slowPeriod + 1 = 6 prices
        // Previous 5 prices: 10, 10, 10, 10, 10 -> prevFast = 10, prevSlow = 10 (prevFast <= prevSlow)
        // Latest tick jumps to 25 -> currFast (10, 10, 25) = 15, currSlow (10, 10, 10, 10, 25) = 13 -> currFast > currSlow
        List<Double> prices = List.of(10.0, 10.0, 10.0, 10.0, 10.0, 25.0);

        StrategySignal signal = evaluator.evaluateMaCrossover(prices, 3, 5);

        assertEquals(StrategySignal.BUY, signal, "Golden cross must trigger BUY signal");
    }

    @Test
    @DisplayName("MA Crossover: Triggers SELL on Death Cross (Fast crosses below Slow)")
    void testMaCrossoverDeathCrossTriggersSell() {
        // Fast period: 3, Slow period: 5
        // Previous prices high, then drops sharply
        List<Double> prices = List.of(30.0, 30.0, 30.0, 30.0, 30.0, 5.0);

        StrategySignal signal = evaluator.evaluateMaCrossover(prices, 3, 5);

        assertEquals(StrategySignal.SELL, signal, "Death cross must trigger SELL signal");
    }

    @Test
    @DisplayName("MA Crossover: Returns HOLD when conditions are not met (no crossover / flat)")
    void testMaCrossoverFlatPricesReturnsHold() {
        List<Double> flatPrices = List.of(20.0, 20.0, 20.0, 20.0, 20.0, 20.0);

        StrategySignal signal = evaluator.evaluateMaCrossover(flatPrices, 3, 5);

        assertEquals(StrategySignal.HOLD, signal, "Flat market with no crossover must produce HOLD");
    }

    @Test
    @DisplayName("MA Crossover: Returns HOLD when historical lookback is insufficient")
    void testMaCrossoverInsufficientDataReturnsHold() {
        List<Double> shortList = List.of(20.0, 21.0, 22.0);

        StrategySignal signal = evaluator.evaluateMaCrossover(shortList, 3, 5);

        assertEquals(StrategySignal.HOLD, signal, "Insufficient data must safely return HOLD");
    }

    // =========================================================================
    // Bollinger Bands Tests
    // =========================================================================

    @Test
    @DisplayName("Bollinger Bands: Triggers BUY when price breaks below lower band (oversold)")
    void testBollingerBandsOversoldTriggersBuy() {
        int period = 20;
        List<Double> prices = new ArrayList<>();
        // 19 prices at 100.0, 20th price plunges to 70.0
        for (int i = 0; i < 19; i++) {
            prices.add(100.0);
        }
        prices.add(70.0);

        StrategySignal signal = evaluator.evaluateBollingerBands(prices, period, 2.0);

        assertEquals(StrategySignal.BUY, signal, "Price piercing lower band must trigger BUY");
    }

    @Test
    @DisplayName("Bollinger Bands: Triggers SELL when price breaks above upper band (overbought)")
    void testBollingerBandsOverboughtTriggersSell() {
        int period = 20;
        List<Double> prices = new ArrayList<>();
        // 19 prices at 100.0, 20th price surges to 130.0
        for (int i = 0; i < 19; i++) {
            prices.add(100.0);
        }
        prices.add(130.0);

        StrategySignal signal = evaluator.evaluateBollingerBands(prices, period, 2.0);

        assertEquals(StrategySignal.SELL, signal, "Price piercing upper band must trigger SELL");
    }

    @Test
    @DisplayName("Bollinger Bands: Returns HOLD when price fluctuates normally inside bands")
    void testBollingerBandsInsideBandsReturnsHold() {
        int period = 20;
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(100.0 + (i % 2 == 0 ? 0.5 : -0.5));
        }

        StrategySignal signal = evaluator.evaluateBollingerBands(prices, period, 2.0);

        assertEquals(StrategySignal.HOLD, signal, "Price contained within bands must produce HOLD");
    }

    @Test
    @DisplayName("Bollinger Bands: Returns HOLD when price series is shorter than period")
    void testBollingerBandsInsufficientDataReturnsHold() {
        List<Double> shortList = List.of(100.0, 101.0, 102.0);

        StrategySignal signal = evaluator.evaluateBollingerBands(shortList, 20, 2.0);

        assertEquals(StrategySignal.HOLD, signal, "Insufficient data must safely return HOLD");
    }
}


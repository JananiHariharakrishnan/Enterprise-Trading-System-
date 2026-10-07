package com.tradingsystem.automation;

import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.model.StrategyType;
import com.tradingsystem.automation.service.AutomationOrderDispatcher;
import com.tradingsystem.automation.service.StrategyAutomationService;
import com.tradingsystem.automation.strategy.StrategyEvaluator;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.messaging.QuotePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyAutomationServiceTest {

    @Mock
    private AutomationOrderDispatcher mockOrderDispatcher;

    private StrategyEvaluator strategyEvaluator;
    private StrategyAutomationService automationService;

    @BeforeEach
    void setUp() {
        strategyEvaluator = new StrategyEvaluator();
        automationService = new StrategyAutomationService(strategyEvaluator, mockOrderDispatcher);
    }

    // =========================================================================
    // 1. Order Placed on Signal Trigger
    // =========================================================================

    @Test
    @DisplayName("Strategy triggers BUY order through Trade API when Golden Cross occurs")
    void testOrderSentThroughTradeApiWhenSignalOccurs() {
        // MA crossover: fast 3, slow 5
        StrategyInstance strategy = new StrategyInstance(
                "strat-ma-1",
                101L,
                "AAPL",
                StrategyType.MA_CROSSOVER,
                50,
                3,
                5,
                20,
                2.0,
                BigDecimal.valueOf(100_000),
                500,
                3
        );
        automationService.registerStrategy(strategy);

        // Seed initial flat price lookback
        automationService.seedPriceHistory("AAPL", new ArrayList<>(List.of(100.0, 100.0, 100.0, 100.0, 100.0)));

        when(mockOrderDispatcher.placeOrder(eq(101L), eq("AAPL"), eq(OrderSide.BUY), eq(50), isNull()))
                .thenReturn(new OrderResponse());

        // Ingest breakout tick that forms a golden cross
        QuotePayload tick = new QuotePayload();
        tick.setSymbol("AAPL");
        tick.setPrice(BigDecimal.valueOf(150.0));

        automationService.onQuote(tick);

        // Verify order was dispatched through S6 Trade API with exact parameters
        verify(mockOrderDispatcher, times(1)).placeOrder(
                eq(101L),
                eq("AAPL"),
                eq(OrderSide.BUY),
                eq(50),
                isNull()
        );
    }

    @Test
    @DisplayName("Bollinger Bands strategy triggers SELL order through Trade API when price hits upper band")
    void testBollingerBandOverboughtTriggersSellOrder() {
        StrategyInstance strategy = new StrategyInstance(
                "strat-bb-1",
                202L,
                "MSFT",
                StrategyType.BOLLINGER_BANDS,
                25,
                5,
                20,
                5,     // 5-period BB
                2.0,
                BigDecimal.valueOf(50_000),
                200,
                3
        );
        automationService.registerStrategy(strategy);

        // Seed 4 prices at 200.0
        automationService.seedPriceHistory("MSFT", new ArrayList<>(List.of(200.0, 200.0, 200.0, 200.0)));

        when(mockOrderDispatcher.placeOrder(eq(202L), eq("MSFT"), eq(OrderSide.SELL), eq(25), isNull()))
                .thenReturn(new OrderResponse());

        // 5th price spikes above upper band
        QuotePayload tick = new QuotePayload();
        tick.setSymbol("MSFT");
        tick.setPrice(BigDecimal.valueOf(250.0));

        automationService.onQuote(tick);

        verify(mockOrderDispatcher, times(1)).placeOrder(
                eq(202L),
                eq("MSFT"),
                eq(OrderSide.SELL),
                eq(25),
                isNull()
        );
    }

    // =========================================================================
    // 2. No Action When Conditions Aren't Met
    // =========================================================================

    @Test
    @DisplayName("No action taken when market conditions remain flat and do not cross")
    void testNoActionWhenConditionsNotMet() {
        StrategyInstance strategy = new StrategyInstance(
                "strat-flat",
                101L,
                "GOOGL",
                StrategyType.MA_CROSSOVER,
                10,
                3,
                5,
                20,
                2.0,
                BigDecimal.valueOf(50_000),
                100,
                3
        );
        automationService.registerStrategy(strategy);

        automationService.seedPriceHistory("GOOGL", new ArrayList<>(List.of(150.0, 150.0, 150.0, 150.0, 150.0)));

        // Live tick is unchanged
        QuotePayload tick = new QuotePayload();
        tick.setSymbol("GOOGL");
        tick.setPrice(BigDecimal.valueOf(150.0));

        automationService.onQuote(tick);

        // Verify Trade API is NEVER called
        verify(mockOrderDispatcher, never()).placeOrder(any(), any(), any(), any(), any());
    }

    // =========================================================================
    // 3. Disabled-State & Immediate Kill-Switch Mid-Cycle
    // =========================================================================

    @Test
    @DisplayName("Strategy disabled before quote event does not place any order")
    void testDisabledStrategyDoesNotPlaceOrder() {
        StrategyInstance strategy = new StrategyInstance(
                "strat-disabled",
                101L,
                "AAPL",
                StrategyType.MA_CROSSOVER,
                50,
                3,
                5,
                20,
                2.0,
                BigDecimal.valueOf(100_000),
                500,
                3
        );
        automationService.registerStrategy(strategy);
        automationService.seedPriceHistory("AAPL", new ArrayList<>(List.of(100.0, 100.0, 100.0, 100.0, 100.0)));

        // Disable strategy
        automationService.disableStrategy("strat-disabled");
        assertFalse(strategy.isEnabled(), "Strategy must be marked disabled");

        // Tick that would otherwise cause a golden cross
        QuotePayload tick = new QuotePayload();
        tick.setSymbol("AAPL");
        tick.setPrice(BigDecimal.valueOf(180.0));

        automationService.onQuote(tick);

        // Verify no order is placed
        verify(mockOrderDispatcher, never()).placeOrder(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Mid-cycle disable: Strategy disabled mid-execution stops order immediately")
    void testMidCycleDisableHaltsExecutionImmediately() {
        StrategyInstance strategy = new StrategyInstance(
                "strat-mid-kill",
                101L,
                "NVDA",
                StrategyType.MA_CROSSOVER,
                20,
                3,
                5,
                20,
                2.0,
                BigDecimal.valueOf(50_000),
                200,
                3
        );
        automationService.registerStrategy(strategy);
        automationService.seedPriceHistory("NVDA", new ArrayList<>(List.of(100.0, 100.0, 100.0, 100.0, 100.0)));

        // Custom evaluator that simulates disable called mid-evaluation cycle
        StrategyEvaluator midCycleDisablingEvaluator = new StrategyEvaluator() {
            @Override
            public com.tradingsystem.automation.model.StrategySignal evaluateMaCrossover(
                    List<Double> prices, int fast, int slow) {
                // User/Admin disables strategy mid-cycle!
                strategy.disable();
                return com.tradingsystem.automation.model.StrategySignal.BUY;
            }
        };

        StrategyAutomationService midCycleService =
                new StrategyAutomationService(midCycleDisablingEvaluator, mockOrderDispatcher);
        midCycleService.registerStrategy(strategy);
        midCycleService.seedPriceHistory("NVDA", new ArrayList<>(List.of(100.0, 100.0, 100.0, 100.0, 100.0)));

        QuotePayload tick = new QuotePayload();
        tick.setSymbol("NVDA");
        tick.setPrice(BigDecimal.valueOf(150.0));

        midCycleService.onQuote(tick);

        // Verification: Even though signal returned BUY, mid-cycle check aborted order placement!
        verify(mockOrderDispatcher, never()).placeOrder(any(), any(), any(), any(), any());
    }

    // =========================================================================
    // 4. Risk Controls: Max Spend, Max Position, Circuit Breaker
    // =========================================================================

    @Test
    @DisplayName("Risk Control: Order blocked when exceeding max spend limit")
    void testMaxSpendBlocksOrder() {
        // Strategy with low max spend: $500
        StrategyInstance strategy = new StrategyInstance(
                "strat-spend-limit",
                101L,
                "TSLA",
                StrategyType.MA_CROSSOVER,
                10, // 10 * 150 = $1,500 > $500
                3,
                5,
                20,
                2.0,
                BigDecimal.valueOf(500),
                500,
                3
        );
        automationService.registerStrategy(strategy);
        automationService.seedPriceHistory("TSLA", new ArrayList<>(List.of(100.0, 100.0, 100.0, 100.0, 100.0)));

        QuotePayload tick = new QuotePayload();
        tick.setSymbol("TSLA");
        tick.setPrice(BigDecimal.valueOf(150.0));

        automationService.onQuote(tick);

        verify(mockOrderDispatcher, never()).placeOrder(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Circuit Breaker: Strategy trips and disables after repeated API failures")
    void testCircuitBreakerTripsAfterRepeatedFailures() {
        StrategyInstance strategy = new StrategyInstance(
                "strat-fail-stop",
                101L,
                "AMZN",
                StrategyType.MA_CROSSOVER,
                5,
                3,
                5,
                20,
                2.0,
                BigDecimal.valueOf(100_000),
                500,
                2 // trips on 2 consecutive failures
        );
        automationService.registerStrategy(strategy);
        automationService.seedPriceHistory("AMZN", new ArrayList<>(List.of(100.0, 100.0, 100.0, 100.0, 100.0)));

        // Mock Trade API failure
        when(mockOrderDispatcher.placeOrder(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Trade API connection failure"));

        QuotePayload tick = new QuotePayload();
        tick.setSymbol("AMZN");
        tick.setPrice(BigDecimal.valueOf(150.0));

        // 1st failure
        automationService.onQuote(tick);
        assertEquals(1, strategy.getConsecutiveFailures());
        assertTrue(strategy.isEnabled());

        // 2nd failure via death cross (price collapses to 10.0) -> trips circuit breaker
        QuotePayload tick2 = new QuotePayload();
        tick2.setSymbol("AMZN");
        tick2.setPrice(BigDecimal.valueOf(10.0));
        automationService.onQuote(tick2);

        assertEquals(2, strategy.getConsecutiveFailures());
        assertFalse(strategy.isEnabled(), "Strategy should be automatically disabled by circuit breaker");
    }
}


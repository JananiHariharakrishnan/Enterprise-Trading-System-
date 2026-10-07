package com.tradingsystem.automation;

import com.tradingsystem.ai.signal.MarketContextStore;
import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.model.StrategySignal;
import com.tradingsystem.automation.model.StrategyType;
import com.tradingsystem.automation.service.AutomationOrderDispatcher;
import com.tradingsystem.automation.service.StrategyAutomationService;
import com.tradingsystem.automation.strategy.StrategyEvaluator;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.messaging.QuotePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyAutomationServiceGapsTest {

    @Mock
    private StrategyEvaluator evaluator;
    @Mock
    private AutomationOrderDispatcher dispatcher;
    @Mock
    private MarketContextStore marketContextStore;

    private StrategyAutomationService service;

    @BeforeEach
    void setUp() {
        service = new StrategyAutomationService(evaluator, dispatcher, marketContextStore);
    }

    @Test
    void registryLifecycle_enablesDisablesAndClears() {
        StrategyInstance strategy = strategy("s1", "AAPL", StrategyType.MA_CROSSOVER, 1, new BigDecimal("1000"));
        service.registerStrategy(strategy);
        service.disableStrategy("missing");
        service.disableStrategy("s1");
        assertFalse(strategy.isEnabled());
        service.enableStrategy("missing");
        service.enableStrategy("s1");
        assertTrue(strategy.isEnabled());
        assertEquals(strategy, service.getStrategy("s1"));
        assertEquals(1, service.getAllStrategies().size());

        service.seedPriceHistory(null, List.of(1.0));
        service.seedPriceHistory("aapl", null);
        service.seedPriceHistory("aapl", List.of(1.0, 2.0));
        assertEquals(List.of(1.0, 2.0), service.getPriceHistory("AAPL"));
        assertTrue(service.getPriceHistory(null).isEmpty());
        assertTrue(service.getPriceHistory("NONE").isEmpty());

        service.unregisterStrategy("missing");
        service.unregisterStrategy("s1");
        assertEquals(null, service.getStrategy("s1"));
        service.clearAll();
        assertTrue(service.getPriceHistory("AAPL").isEmpty());
        assertTrue(service.getAllStrategies().isEmpty());
    }

    @Test
    void onQuote_ignoresIncompleteQuotesAndOtherSymbols() {
        service.registerStrategy(strategy("s1", "MSFT", StrategyType.MA_CROSSOVER, 1, new BigDecimal("1000")));

        assertTrue(service.onQuote(null).isEmpty());
        QuotePayload noSymbol = quote("IGNORED", null);
        noSymbol.setSymbol(null);
        assertTrue(service.onQuote(noSymbol).isEmpty());
        assertTrue(service.onQuote(quote("AAPL", null)).isEmpty());

        List<String> outcomes = service.onQuote(quote("AAPL", new BigDecimal("10")));
        assertTrue(outcomes.isEmpty());
        verify(evaluator, never()).evaluateMaCrossover(any(), anyInt(), anyInt());
    }

    @Test
    void onQuote_usesMarketContextWhenHistoryWasNotSeeded() {
        StrategyInstance strategy = strategy("ma", "AAPL", StrategyType.MA_CROSSOVER, 2, new BigDecimal("5000"));
        service.registerStrategy(strategy);
        when(marketContextStore.getCloses("AAPL")).thenReturn(null);
        when(evaluator.evaluateMaCrossover(any(), anyInt(), anyInt())).thenReturn(StrategySignal.HOLD);

        List<String> outcomes = service.onQuote(quote("aapl", new BigDecimal("15.50")));

        verify(marketContextStore).append("AAPL", 15.50);
        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0).contains("HOLD"));
    }

    @Test
    void onQuote_prefersSeededHistoryOverMarketContext() {
        service.registerStrategy(strategy("bb", "MSFT", StrategyType.BOLLINGER_BANDS, 1, new BigDecimal("5000")));
        service.seedPriceHistory("MSFT", new ArrayList<>(List.of(20.0, 21.0)));
        when(evaluator.evaluateBollingerBands(any(), anyInt(), eq(2.0))).thenReturn(StrategySignal.SELL);
        when(dispatcher.placeOrder(eq(9L), eq("MSFT"), eq(OrderSide.SELL), eq(1), isNull()))
                .thenReturn(new OrderResponse());

        List<String> outcomes = service.onQuote(quote("MSFT", new BigDecimal("22")));

        verify(marketContextStore, never()).append(anyString(), anyDouble());
        assertTrue(outcomes.get(0).contains("SUCCESS"));
        assertTrue(outcomes.get(0).contains("SELL"));
    }

    @Test
    void onQuote_blocksRiskAndReportsDisabledAbortedAndFailedOrders() {
        StrategyInstance blocked = strategy("risk", "TSLA", StrategyType.MA_CROSSOVER, 10, new BigDecimal("1"));
        service.registerStrategy(blocked);
        when(evaluator.evaluateMaCrossover(any(), anyInt(), anyInt())).thenReturn(StrategySignal.BUY);

        List<String> blockedOutcomes = service.onQuote(quote("TSLA", new BigDecimal("50")));
        assertTrue(blockedOutcomes.get(0).contains("risk bounds"));
        verify(dispatcher, never()).placeOrder(any(), any(), any(), any(), any());

        StrategyInstance disabled = strategy("off", "NVDA", StrategyType.MA_CROSSOVER, 1, new BigDecimal("1000"));
        disabled.disable();
        service.registerStrategy(disabled);
        assertTrue(service.onQuote(quote("NVDA", new BigDecimal("10"))).get(0).contains("disabled"));

        BoundaryStrategy boundary = new BoundaryStrategy();
        service.registerStrategy(boundary);
        assertTrue(service.onQuote(quote("AMD", new BigDecimal("30"))).get(0).contains("Cancelled at execution boundary"));

        StrategyInstance failing = strategy("fail", "AMZN", StrategyType.MA_CROSSOVER, 1, new BigDecimal("1000"));
        service.registerStrategy(failing);
        when(dispatcher.placeOrder(eq(9L), eq("AMZN"), eq(OrderSide.BUY), eq(1), isNull()))
                .thenThrow(new RuntimeException("trade api down"));
        assertTrue(service.onQuote(quote("AMZN", new BigDecimal("12"))).get(0).contains("FAILED"));
        assertEquals(1, failing.getConsecutiveFailures());
    }

    @Test
    void onQuote_unknownTypeHoldsAndLocalHistoryTrimsAtFiveHundred() {
        StrategyAutomationService local = new StrategyAutomationService(evaluator, dispatcher);
        StrategyInstance untyped = strategy("none", "IBM", null, 1, new BigDecimal("1000"));
        local.registerStrategy(untyped);

        List<String> held = local.onQuote(quote("IBM", new BigDecimal("5")));
        assertTrue(held.get(0).contains("HOLD"));
        verify(evaluator, never()).evaluateMaCrossover(any(), anyInt(), anyInt());
        verify(evaluator, never()).evaluateBollingerBands(any(), anyInt(), anyDouble());

        List<Double> almostFull = Collections.synchronizedList(new ArrayList<>() {
            @Override
            public boolean isEmpty() {
                return true;
            }
        });
        for (int i = 0; i < 500; i++) {
            almostFull.add(1.0);
        }
        @SuppressWarnings("unchecked")
        Map<String, List<Double>> history = (Map<String, List<Double>>) ReflectionTestUtils.getField(local, "priceHistory");
        history.put("ORCL", almostFull);

        local.onQuote(quote("ORCL", BigDecimal.ONE));
        assertEquals(500, local.getPriceHistory("ORCL").size());
    }

    private static QuotePayload quote(String symbol, BigDecimal price) {
        QuotePayload quote = new QuotePayload();
        quote.setSymbol(symbol);
        quote.setPrice(price);
        return quote;
    }

    private static StrategyInstance strategy(String id, String symbol, StrategyType type, int quantity, BigDecimal maxSpend) {
        return new StrategyInstance(id, 9L, symbol, type, quantity, 3, 5, 20, 2.0, maxSpend, 500, 3);
    }

    private static final class BoundaryStrategy extends StrategyInstance {
        private int checks;

        private BoundaryStrategy() {
            super("boundary", 9L, "AMD", StrategyType.MA_CROSSOVER, 1, 3, 5, 20, 2.0,
                    BigDecimal.valueOf(100_000), 500, 3);
        }

        @Override
        public boolean isEnabled() {
            return ++checks < 3;
        }
    }
}

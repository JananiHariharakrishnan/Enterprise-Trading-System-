package com.tradingsystem.ai.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.model.AiSignal;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalEngineGapsTest {

    @Mock
    private MarketContextStore contextStore;
    @Mock
    private SignalCooldownManager cooldownManager;
    @Mock
    private SignalCadenceManager cadenceManager;
    @Mock
    private TradeSignalStore signalStore;
    @Mock
    private LlmGateway llmGateway;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private Acknowledgment acknowledgment;

    private SignalEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SignalEngine(
                contextStore,
                cooldownManager,
                cadenceManager,
                signalStore,
                llmGateway,
                kafkaTemplate,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(engine, "rsiPeriod", 14);
        ReflectionTestUtils.setField(engine, "emaShortPeriod", 9);
        ReflectionTestUtils.setField(engine, "emaLongPeriod", 21);

        lenient().when(contextStore.getCloses(anyString())).thenReturn(List.of(100.0, 101.0, 102.0));
        lenient().when(cooldownManager.isOnCooldown(anyString())).thenReturn(false);
        lenient().when(cadenceManager.shouldRecompute(anyString())).thenReturn(true);
        lenient().when(cadenceManager.getCadenceIntervalSeconds()).thenReturn(60L);
    }

    @Test
    void onMarketData_readsWrappedDirectAndFallbackPrices() {
        when(cadenceManager.shouldRecompute(anyString())).thenReturn(false);

        engine.onMarketData(record("{\"payload\":{\"symbol\":\"aapl\",\"price\":190.5}}"), acknowledgment);
        engine.onMarketData(record("{\"symbol\":\"spy\",\"executorPrice\":548.25}"), acknowledgment);
        engine.onMarketData(record("{\"symbol\":\"INFY.NS\",\"price\":0,\"executedPrice\":1500.5}"), acknowledgment);
        engine.onMarketData(record("{\"symbol\":\"   \"}"), acknowledgment);
        engine.onMarketData(record("{\"ignored\":true}"), acknowledgment);

        verify(contextStore).append("AAPL", 190.5);
        verify(contextStore).append("SPY", 548.25);
        verify(contextStore).append("INFY.NS", 1500.5);
        verify(acknowledgment, org.mockito.Mockito.times(5)).acknowledge();
        verify(llmGateway, never()).sendPrompt(anyString());
    }

    @Test
    void onMarketData_resolvesMissingPriceAndAcknowledgesBadPayloads() {
        when(cadenceManager.shouldRecompute(anyString())).thenReturn(false);
        when(contextStore.getCloses("AAPL")).thenReturn(List.of());
        when(contextStore.getCloses("ZZZZ")).thenReturn(List.of(10.0, 11.5));
        when(contextStore.getCloses("TATASTEEL.BO")).thenReturn(List.of());

        engine.onMarketData(record("{\"symbol\":\"AAPL\"}"), acknowledgment);
        engine.onMarketData(record("{\"symbol\":\"ZZZZ\",\"price\":-1}"), acknowledgment);
        engine.onMarketData(record("{\"payload\":{\"symbol\":\"TATASTEEL.BO\",\"executorPrice\":0,\"executedPrice\":0}}"), acknowledgment);
        engine.onMarketData(record("not-json"), acknowledgment);

        verify(contextStore).append("AAPL", 232.50);
        verify(contextStore).append("ZZZZ", 11.5);
        verify(contextStore).append("TATASTEEL.BO", 164.20);
        verify(acknowledgment, org.mockito.Mockito.times(4)).acknowledge();
        verify(signalStore, never()).save(any());
    }

    @Test
    void onMarketData_recomputesWhenCadenceAllows() {
        when(llmGateway.sendPrompt(anyString())).thenReturn(json("BUY", 0.8, "Bullish alignment at this price level."));

        engine.onMarketData(record("{\"symbol\":\"AAPL\",\"price\":210}"), acknowledgment);

        verify(signalStore).save(any(AiSignal.class));
        verify(cadenceManager).recordRecomputation("AAPL");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processQuotePoint_clampsParsedSignalAndPublishes() {
        when(llmGateway.sendPrompt(anyString())).thenReturn(
                "```json\n{\"action\":\"MAYBE\",\"confidence\":1.4,\"reasoning\":\"Steady trend at this level.\"}\n```"
        );

        PortfolioContext portfolio = new PortfolioContext();
        portfolio.setBuyingPower(new BigDecimal("1000"));
        portfolio.setCashBalance(new BigDecimal("800"));

        engine.processQuotePoint(" aapl ", 210.0, portfolio);

        ArgumentCaptor<AiSignal> saved = ArgumentCaptor.forClass(AiSignal.class);
        verify(signalStore).save(saved.capture());
        assertEquals("HOLD", saved.getValue().getAction());
        assertEquals(0.94, saved.getValue().getConfidence());
        assertEquals("Steady trend at this level.", saved.getValue().getReasoning());
        verify(kafkaTemplate).send(eq("trade-signals"), eq("AAPL"), anyString());
        verify(cooldownManager).markCooldown("AAPL");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmGateway).sendPrompt(prompt.capture());
        assertTrue(prompt.getValue().contains("USER PORTFOLIO CONTEXT"));
    }

    @Test
    void processQuotePoint_fallsBackWhenResponseIsGenericUnparseableOrFails() {
        when(llmGateway.sendPrompt(anyString()))
                .thenReturn("{\"action\":\"BUY\",\"confidence\":0.2,\"reasoning\":\"warrant a neutral view\"}")
                .thenReturn("not json")
                .thenThrow(new RuntimeException("llm offline"));

        engine.processQuotePoint("AAPL", 210.0);
        engine.processQuotePoint("AAPL", 210.0);
        engine.processQuotePoint("AAPL", 210.0);

        verify(signalStore, org.mockito.Mockito.times(3)).save(any(AiSignal.class));
    }

    @Test
    void processQuotePoint_skipsInvalidCooldownAndBlankQuotes() {
        when(cooldownManager.isOnCooldown("AAPL")).thenReturn(true);

        engine.processQuotePoint(null, 10);
        engine.processQuotePoint("   ", 10);
        engine.processQuotePoint("AAPL", 0);
        engine.processQuotePoint("EMA", 50);
        engine.processQuotePoint("AAPL", 50);

        verify(signalStore, never()).save(any());
        verify(llmGateway, never()).sendPrompt(anyString());
    }

    @Test
    void processQuotePoint_stillSavesWhenKafkaPublishFails() {
        when(llmGateway.sendPrompt(anyString())).thenReturn(json("SELL", 0.5, "Distribution pressure is building here."));
        doThrow(new RuntimeException("broker down")).when(kafkaTemplate).send(any(), any(), any());

        engine.processQuotePoint("RELIANCE.NS", 100);

        ArgumentCaptor<AiSignal> saved = ArgumentCaptor.forClass(AiSignal.class);
        verify(signalStore).save(saved.capture());
        assertEquals("SELL", saved.getValue().getAction());
        assertEquals(0.72, saved.getValue().getConfidence());
    }

    @Test
    void generateSignalForSymbol_honorsValidationCooldownAndStore() {
        assertNull(engine.generateSignalForSymbol(null, 1.0, true));
        assertNull(engine.generateSignalForSymbol("  ", 1.0, false));
        assertNull(engine.generateSignalForSymbol("RSI", 10.0, true));

        AiSignal stored = new AiSignal("AAPL", "HOLD", 0.75, "cached");
        when(signalStore.getBySymbol("AAPL")).thenReturn(Optional.of(stored));
        when(llmGateway.sendPrompt(anyString())).thenReturn(json("HOLD", 0.8, "Range is intact at this price."));

        AiSignal bypassed = engine.generateSignalForSymbol(" aapl ", null, true);
        AiSignal throttled = engine.generateSignalForSymbol("AAPL", -1.0, false);

        assertEquals("AAPL", bypassed.getSymbol());
        assertEquals("HOLD", throttled.getAction());
        verify(cooldownManager).resetCooldown("AAPL");
        verify(cadenceManager).resetCadence("AAPL");
        verify(cadenceManager, org.mockito.Mockito.atLeastOnce()).recordRecomputation("AAPL");
    }

    @Test
    void isKnownReferenceSymbol_matchesSeededUniverseOnly() {
        assertFalse(SignalEngine.isKnownReferenceSymbol(null));
        assertFalse(SignalEngine.isKnownReferenceSymbol("  "));
        assertFalse(SignalEngine.isKnownReferenceSymbol("ZZZZ"));
        assertTrue(SignalEngine.isKnownReferenceSymbol(" aapl "));
        assertTrue(SignalEngine.isKnownReferenceSymbol("infy.ns"));
        assertTrue(SignalEngine.isKnownReferenceSymbol("HDFCBANK.NS"));
    }

    @Test
    void ruleBasedSignal_coversBuySellHoldWithAndWithoutHoldings() throws Exception {
        PortfolioContext held = new PortfolioContext();
        held.setCurrentHoldingQuantity(5);
        held.setCurrentAverageCost(new BigDecimal("40"));

        PortfolioContext heldWithoutCost = new PortfolioContext();
        heldWithoutCost.setCurrentHoldingQuantity(2);

        assertEquals("BUY", rule("AAPL", List.of(10.0), 55, 20, 10, null).getAction());
        assertTrue(rule("AAPL", List.of(), 55, 20, 10, held).getReasoning().contains("Expanding"));
        assertTrue(rule("AAPL", List.of(12.0), 55, 20, 10, heldWithoutCost).getReasoning().contains("0.00"));

        assertEquals("SELL", rule("AAPL", List.of(9.0), 55, 10, 20, null).getAction());
        assertTrue(rule("AAPL", List.of(9.0), 80, 20, 10, null).getReasoning().contains("overbought"));
        assertTrue(rule("AAPL", List.of(9.0), 80, 20, 10, held).getReasoning().contains("risk reduction"));
        assertTrue(rule("AAPL", List.of(9.0), 30, 20, 10, null).getReasoning().contains("weakening"));
        assertTrue(rule("AAPL", List.of(9.0), 50, 10, 20, held).getReasoning().contains("dipping below"));

        assertEquals("HOLD", rule("AAPL", List.of(), 45.2, 20, 19, null).getAction());
        assertTrue(rule("AAPL", List.of(11.0), 45.2, 20, 19, held).getReasoning().contains("consolidation"));
        assertEquals(100.0, rule("AAPL", List.of(), 45.2, 20, 19, null).getCurrentPrice());
    }

    private AiSignal rule(String symbol, List<Double> closes, double rsi, double ema9, double ema21, PortfolioContext portfolio) throws Exception {
        Method method = SignalEngine.class.getDeclaredMethod(
                "generateRuleBasedSignal",
                String.class,
                List.class,
                double.class,
                double.class,
                double.class,
                PortfolioContext.class
        );
        method.setAccessible(true);
        return (AiSignal) method.invoke(engine, symbol, closes, rsi, ema9, ema21, portfolio);
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("market-data", 0, 0L, "key", json);
    }

    private static String json(String action, double confidence, String reasoning) {
        return "{\"action\":\"" + action + "\",\"confidence\":" + confidence + ",\"reasoning\":\"" + reasoning + "\"}";
    }
}

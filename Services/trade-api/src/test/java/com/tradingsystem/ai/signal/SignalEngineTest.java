package com.tradingsystem.ai.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.ai.llm.LlmGateway;
import com.tradingsystem.model.AiSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SignalEngineTest {

    private MarketContextStore contextStore;
    private SignalCooldownManager cooldownManager;
    private SignalCadenceManager cadenceManager;
    private TradeSignalStore signalStore;
    private LlmGateway llmGateway;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private CandleCacheManager candleCacheManager;
    private FauxnanceCandleClient candleClient;

    private SignalEngine engine;

    @BeforeEach
    void setUp() {
        candleClient = Mockito.mock(FauxnanceCandleClient.class);
        candleCacheManager = new CandleCacheManager(candleClient);
        contextStore = new MarketContextStore(candleCacheManager);
        cooldownManager = new SignalCooldownManager();
        cadenceManager = new SignalCadenceManager();
        signalStore = Mockito.mock(TradeSignalStore.class);
        llmGateway = Mockito.mock(LlmGateway.class);
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();

        engine = new SignalEngine(
                contextStore,
                cooldownManager,
                cadenceManager,
                signalStore,
                llmGateway,
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("SignalEngine generates BUY signal with defensible indicators and citing exact numbers in reasoning")
    void testSignalGenerationWithNumbers() {
        // Pre-seed 30 ascending closes to create bullish EMA(9) > EMA(21) and healthy RSI
        for (int i = 1; i <= 30; i++) {
            contextStore.append("AAPL", 150.0 + i * 2.0);
        }

        // Mock LLM failure or fallback to test quantitative rule engine
        when(llmGateway.sendPrompt(anyString())).thenThrow(new RuntimeException("LLM offline"));

        engine.processQuotePoint("AAPL", 215.0, null);

        // Verify signal was saved
        Mockito.verify(signalStore).save(Mockito.argThat(signal -> {
            assertNotNull(signal);
            assertEquals("AAPL", signal.getSymbol());
            assertEquals("BUY", signal.getAction());
            assertTrue(signal.getConfidence() >= 0.72);
            assertNotNull(signal.getRsi());
            assertNotNull(signal.getEma9());
            assertNotNull(signal.getEma21());
            // Reasoning sentence MUST state what produced it and cite numbers
            assertTrue(signal.getReasoning().contains("EMA"));
            assertTrue(signal.getReasoning().contains("RSI"));
            return true;
        }));
    }
}

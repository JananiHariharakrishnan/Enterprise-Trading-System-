package com.tradingsystem.ai.signal;

import com.tradingsystem.mapper.AiSignalMapper;
import com.tradingsystem.model.AiSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeSignalStoreValidationTest {

    private AiSignalMapper mapper;
    private DataSource dataSource;
    private TradeSignalStore store;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(AiSignalMapper.class);
        dataSource = Mockito.mock(DataSource.class);
        store = new TradeSignalStore(mapper, dataSource);
    }

    @Test
    @DisplayName("isValidSymbol rejects technical indicators, grammar words, and allows real stock tickers")
    void testIsValidSymbol() {
        // Discrepancy words reported by user
        assertFalse(TradeSignalStore.isValidSymbol("EMA"));
        assertFalse(TradeSignalStore.isValidSymbol("RSI"));
        assertFalse(TradeSignalStore.isValidSymbol("YOUR"));
        assertFalse(TradeSignalStore.isValidSymbol("BUY"));
        assertFalse(TradeSignalStore.isValidSymbol("SELL"));
        assertFalse(TradeSignalStore.isValidSymbol("HOLD"));
        assertFalse(TradeSignalStore.isValidSymbol("TRADE"));
        assertFalse(TradeSignalStore.isValidSymbol("MACD"));
        assertFalse(TradeSignalStore.isValidSymbol("VWAP"));
        assertFalse(TradeSignalStore.isValidSymbol("IN"));
        assertFalse(TradeSignalStore.isValidSymbol("TODAY"));
        assertFalse(TradeSignalStore.isValidSymbol("NOW"));
        assertFalse(TradeSignalStore.isValidSymbol("DAY"));

        // Valid tickers
        assertTrue(TradeSignalStore.isValidSymbol("AAPL"));
        assertTrue(TradeSignalStore.isValidSymbol("SPY"));
        assertTrue(TradeSignalStore.isValidSymbol("INFY.NS"));
        assertTrue(TradeSignalStore.isValidSymbol("RELIANCE.NS"));
        assertTrue(TradeSignalStore.isValidSymbol("TATASTEEL.BO"));
    }

    @Test
    @DisplayName("save refuses to persist invalid symbols like EMA or RSI to cache or DB")
    void testSaveRefusesInvalidSymbols() {
        AiSignal emaSignal = new AiSignal();
        emaSignal.setSymbol("EMA");
        emaSignal.setAction("SELL");
        emaSignal.setConfidence(0.88);
        emaSignal.setReasoning("Fake EMA signal");

        store.save(emaSignal);

        verify(mapper, never()).upsertSignal(any());
        assertTrue(store.getBySymbol("EMA").isEmpty());
    }

    @Test
    @DisplayName("save persists and retrieves valid stock symbols")
    void testSaveAllowsValidSymbols() {
        AiSignal aaplSignal = new AiSignal();
        aaplSignal.setSymbol("AAPL");
        aaplSignal.setAction("BUY");
        aaplSignal.setConfidence(0.85);
        aaplSignal.setReasoning("Bullish AAPL signal");

        store.save(aaplSignal);

        verify(mapper, times(1)).upsertSignal(aaplSignal);
        Optional<AiSignal> found = store.getBySymbol("AAPL");
        assertTrue(found.isPresent());
        assertEquals("AAPL", found.get().getSymbol());
    }
}

package com.tradingsystem.ai.signal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CandleCacheManagerTest {

    @Test
    @DisplayName("CandleCacheManager caches candles for 24h and serves subsequent queries from cache without calling client")
    void testCachingBehavior() {
        FauxnanceCandleClient mockClient = mock(FauxnanceCandleClient.class);
        when(mockClient.fetchDailyCloses(eq("AAPL"))).thenReturn(List.of(230.0, 231.5, 232.0, 234.0));

        CandleCacheManager manager = new CandleCacheManager(mockClient);

        // First call: MISS -> calls client
        List<Double> first = manager.getClosingPrices("AAPL");
        assertEquals(4, first.size());
        verify(mockClient, times(1)).fetchDailyCloses("AAPL");

        // Second call: HIT -> does NOT call client again (conserves quota)
        List<Double> second = manager.getClosingPrices("AAPL");
        assertEquals(4, second.size());
        verify(mockClient, times(1)).fetchDailyCloses("AAPL");

        // Cache duration description is accurate
        String desc = manager.getCacheDurationDescription();
        assertTrue(desc.contains("24 hours"));
        assertTrue(desc.contains("86400 seconds") || desc.contains("86,400 seconds"));
    }
}

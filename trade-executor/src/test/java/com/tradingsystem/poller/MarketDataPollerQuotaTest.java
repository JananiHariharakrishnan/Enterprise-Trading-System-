package com.tradingsystem.poller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MarketDataPollerQuotaTest {

    @Test
    void eightSymbolsAt60SecondsUse1440RequestsPerDay() {

        assertEquals(
                1,
                MarketDataPoller.requestCountPerPoll(8)
        );

        assertEquals(
                1440,
                MarketDataPoller.requestsPerDay(8, 60)
        );

        assertTrue(
                MarketDataPoller.requestsPerDay(8, 60) <= 2000
        );
    }

    @Test
    void SymbolsAt60SecondsUse1440RequestsPerDay() {

        assertEquals(
                1,
                MarketDataPoller.requestCountPerPoll(25)
        );

        assertEquals(
                1440,
                MarketDataPoller.requestsPerDay(25, 60)
        );

        assertTrue(
                MarketDataPoller.requestsPerDay(25, 60) <= 2000
        );
    }

    @Test
    void SymbolsAt60SecondsUse2880RequestsPerDay() {

        assertEquals(
                2,
                MarketDataPoller.requestCountPerPoll(26)
        );

        assertEquals(
                2880,
                MarketDataPoller.requestsPerDay(26, 60)
        );

        assertTrue(
                MarketDataPoller.requestsPerDay(26, 60) > 2000
        );
    }

    @Test
    void SymbolsAt60SecondsUse5760RequestsPerDay() {

        assertEquals(
                4,
                MarketDataPoller.requestCountPerPoll(100)
        );

        assertEquals(
                5760,
                MarketDataPoller.requestsPerDay(100, 60)
        );

        assertTrue(
                MarketDataPoller.requestsPerDay(100, 60) > 2000
        );
    }

    @Test
    void SymbolsRequire173SecondMinimumInterval() {

        assertEquals(
                173,
                MarketDataPoller.minimumIntervalSeconds(100)
        );
    }

    @Test
    void oneBatchRequires44SecondMinimumInterval() {

        assertEquals(
                44,
                MarketDataPoller.minimumIntervalSeconds(1)
        );

        assertEquals(
                44,
                MarketDataPoller.minimumIntervalSeconds(25)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "1,1",
            "24,1",
            "25,1",
            "26,2",
            "49,2",
            "50,2",
            "51,3",
            "74,3",
            "75,3",
            "76,4",
            "100,4"
    })
    void requestCountFollows25SymbolBatchRule(
            int symbolCount,
            int expectedRequests) {

        assertEquals(
                expectedRequests,
                MarketDataPoller.requestCountPerPoll(symbolCount)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "1,44",
            "25,44",
            "26,87",
            "50,87",
            "51,130",
            "75,130",
            "76,173",
            "100,173"
    })
    void minimumIntervalMatchesDailyQuotaCalculation(
            int symbolCount,
            long expectedMinimumInterval) {

        assertEquals(
                expectedMinimumInterval,
                MarketDataPoller.minimumIntervalSeconds(symbolCount)
        );
    }


    @Test
    void SymbolsAt60SecondsAreRejectedDuringPolling() {

        SymbolSource symbolSource =
                mock(SymbolSource.class);

        FauxnanceClient fauxnanceClient =
                mock(FauxnanceClient.class);

        MarketDataProducer producer =
                mock(MarketDataProducer.class);

        List<String> symbols =
                java.util.stream.IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> "SYM" + i)
                        .toList();

        when(symbolSource.findSymbols())
                .thenReturn(symbols);

        MarketDataPollerProperties properties =
                new MarketDataPollerProperties();

        properties.setIntervalSeconds(60);

        MarketDataPoller poller =
                new MarketDataPoller(
                        symbolSource,
                        fauxnanceClient,
                        producer,
                        properties
                );

        assertThrows(
                IllegalStateException.class,
                poller::pollMarketData
        );

        verifyNoInteractions(fauxnanceClient);
        verifyNoInteractions(producer);
    }

    @Test
    void SymbolsAt173SecondsAreAllowed() {

        SymbolSource symbolSource =
                mock(SymbolSource.class);

        FauxnanceClient fauxnanceClient =
                mock(FauxnanceClient.class);

        MarketDataProducer producer =
                mock(MarketDataProducer.class);

        List<String> symbols =
                java.util.stream.IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> "SYM" + i)
                        .toList();

        when(symbolSource.findSymbols())
                .thenReturn(symbols);

        when(fauxnanceClient.getQuotes(anyList()))
                .thenReturn(List.of());

        MarketDataPollerProperties properties =
                new MarketDataPollerProperties();

        properties.setIntervalSeconds(173);

        MarketDataPoller poller =
                new MarketDataPoller(
                        symbolSource,
                        fauxnanceClient,
                        producer,
                        properties
                );

        assertDoesNotThrow(
                poller::pollMarketData
        );

        verify(
                fauxnanceClient,
                times(4)
        ).getQuotes(anyList());

        verifyNoInteractions(producer);
    }

    @Test
    void requestsPerDayCalculationIsIntegerBased() {

        assertEquals(
                1440,
                MarketDataPoller.requestsPerDay(8, 60)
        );

        assertEquals(
                2880,
                MarketDataPoller.requestsPerDay(26, 60)
        );

        assertEquals(
                5760,
                MarketDataPoller.requestsPerDay(100, 60)
        );
    }
}
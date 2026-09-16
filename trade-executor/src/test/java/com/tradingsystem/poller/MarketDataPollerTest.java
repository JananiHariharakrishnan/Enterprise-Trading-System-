package com.tradingsystem.poller;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataPollerTest {

    @Mock
    private SymbolSource symbolSource;

    @Mock
    private FauxnanceClient fauxnanceClient;

    @Mock
    private MarketDataProducer marketDataProducer;

    @Test
    void batchOfUpTo25SymbolsIsFetchedInOneRequest() {

        List<String> symbols = List.of(
                "AAPL", "MSFT", "TSLA", "NVDA",
                "AMZN", "META", "GOOG", "NFLX"
        );

        when(symbolSource.findSymbols()).thenReturn(symbols);
        when(fauxnanceClient.getQuotes(symbols))
                .thenReturn(symbols.stream()
                        .map(this::quote)
                        .toList());

        MarketDataPoller poller = createPoller(60);

        poller.pollMarketData();

        verify(fauxnanceClient).getQuotes(symbols);
        verify(fauxnanceClient, times(1)).getQuotes(any());
    }

    @Test
    void eachQuoteIsPublishedAsOwnMessageKeyedBySymbol() {

        List<String> symbols = List.of("AAPL", "MSFT", "TSLA");

        when(symbolSource.findSymbols()).thenReturn(symbols);

        List<Quote> quotes = List.of(
                quote("AAPL"),
                quote("MSFT"),
                quote("TSLA")
        );

        when(fauxnanceClient.getQuotes(symbols))
                .thenReturn(quotes);

        MarketDataPoller poller = createPoller(60);

        poller.pollMarketData();

        ArgumentCaptor<Quote> quoteCaptor = ArgumentCaptor.forClass(Quote.class);

        verify(marketDataProducer, times(3)).publishQuote(quoteCaptor.capture());

        assertEquals(
                List.of("AAPL", "MSFT", "TSLA"),
                quoteCaptor.getAllValues()
                        .stream()
                        .map(Quote::symbol)
                        .toList()
        );
    }

    @Test
    void declaredEightSymbolSetStaysInsideDailyQuota() {

        int symbolCount = 8;
        long intervalSeconds = 60;

        long requestsPerPoll =
                MarketDataPoller.requestCountPerPoll(symbolCount);

        long requestsPerDay =
                MarketDataPoller.requestsPerDay(
                        symbolCount,
                        intervalSeconds
                );

        assertEquals(1, requestsPerPoll);
        assertEquals(1440, requestsPerDay);
        assertTrue(requestsPerDay <= 2000);
    }

    @Test
    void intervalFloorIsEnforced() {

        assertThrows(
                IllegalArgumentException.class,
                () -> createPoller(30)
        );
    }

    private MarketDataPoller createPoller(long intervalSeconds) {

        MarketDataPollerProperties properties =
                new MarketDataPollerProperties();

        properties.setIntervalSeconds(intervalSeconds);

        return new MarketDataPoller(
                symbolSource,
                fauxnanceClient,
                marketDataProducer,
                properties
        );
    }

    private Quote quote(String symbol) {

        return new Quote(
                symbol,
                BigDecimal.valueOf(232.71),
                "USD",
                BigDecimal.valueOf(0.21),
                BigDecimal.valueOf(0.09),
                BigDecimal.valueOf(232.50),
                "open",
                false,
                Instant.parse("2026-09-28T09:14:58Z")
        );
    }
}
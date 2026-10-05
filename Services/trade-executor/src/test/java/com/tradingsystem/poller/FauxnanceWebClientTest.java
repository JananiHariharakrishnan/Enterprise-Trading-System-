package com.tradingsystem.poller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


import java.math.BigDecimal;
import java.time.Instant;


class FauxnanceWebClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }
    @Test
    void getQuotes_nullOrEmptySymbols_returnsEmptyList() {
        FauxnanceWebClient client = new FauxnanceWebClient("http://localhost", "/quotes", "key");

        assertTrue(client.getQuotes(null).isEmpty());
        assertTrue(client.getQuotes(List.of()).isEmpty());
    }

    @Test
    void getQuote_nullOrBlankSymbol_returnsNull() {
        FauxnanceWebClient client = new FauxnanceWebClient("http://localhost", "/quotes", "key");

        assertNull(client.getQuote(null));
        assertNull(client.getQuote(""));
        assertNull(client.getQuote("   "));
    }
    @Test
    void getQuotesMapsFauxnanceResponseCorrectly()
            {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody("""
                                {
                                  "data": {
                                    "quotes": [
                                      {
                                        "symbol": "AAPL",
                                        "stale": false,
                                        "quote": {
                                          "symbol": "AAPL",
                                          "price": 333.588,
                                          "bid": 333.55,
                                          "ask": 333.62,
                                          "spreadBps": 1.8587,
                                          "currency": "USD",
                                          "change": 2.248,
                                          "changePercent": 0.678457,
                                          "previousClose": 331.34,
                                          "asOf": "2026-09-16T14:02:09Z",
                                          "marketState": "unknown"
                                        }
                                      }
                                    ]
                                  },
                                  "meta": {}
                                }
                                """)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/v1").toString(),
                        "/quotes",
                        "test-api-key"
                );

        List<Quote> quotes =
                client.getQuotes(List.of("AAPL"));

        assertEquals(1, quotes.size());

        Quote quote = quotes.get(0);

        assertEquals("AAPL", quote.symbol());
        assertEquals(
                BigDecimal.valueOf(333.588),
                quote.price()
        );
        assertEquals("USD", quote.currency());
        assertEquals(
                BigDecimal.valueOf(2.248),
                quote.change()
        );
        assertEquals(
                BigDecimal.valueOf(0.678457),
                quote.changePercent()
        );
        assertEquals(
                BigDecimal.valueOf(331.34),
                quote.previousClose()
        );
        assertEquals("unknown", quote.marketState());
        assertFalse(quote.stale());

        assertEquals(
                Instant.parse("2026-09-16T14:02:09Z"),
                quote.quoteAsOf()
        );
    }

    @Test
    void getQuotesSendsCorrectApiKeyAndSymbols()
            throws Exception {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody("""
                                {
                                  "data": {
                                    "quotes": []
                                  }
                                }
                                """)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/v1").toString(),
                        "/quotes",
                        "my-secret-key"
                );

        client.getQuotes(
                List.of("AAPL", "MSFT")
        );

        RecordedRequest request =
                server.takeRequest();

        assertEquals(
                "GET",
                request.getMethod()
        );

        assertEquals(
                "/v1/quotes?symbols=AAPL,MSFT",
                request.getPath()
        );

        assertEquals(
                "my-secret-key",
                request.getHeader("X-API-Key")
        );
    }

    @Test
    void getQuotesReturnsEmptyListForNullSymbols() {

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getQuotesReturnsEmptyListForEmptySymbols() {

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(List.of());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getQuotesReturnsEmptyListWhenResponseIsNull()
           {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(204)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(List.of("AAPL"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getQuotesReturnsEmptyListWhenDataIsNull()
            {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody("""
                                {
                                  "data": null
                                }
                                """)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(List.of("AAPL"));

        assertTrue(result.isEmpty());
    }

    @Test
    void getQuotesReturnsEmptyListWhenQuotesIsNull()
             {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody("""
                                {
                                  "data": {
                                    "quotes": null
                                  }
                                }
                                """)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(List.of("AAPL"));

        assertTrue(result.isEmpty());
    }

    @Test
    void getQuotesIgnoresEntriesWithNullQuote()
     {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody("""
                                {
                                  "data": {
                                    "quotes": [
                                      {
                                        "symbol": "SATYAM.BO",
                                        "stale": null,
                                        "quote": null
                                      },
                                      {
                                        "symbol": "AAPL",
                                        "stale": false,
                                        "quote": {
                                          "symbol": "AAPL",
                                          "price": 333.588,
                                          "currency": "USD",
                                          "change": 2.248,
                                          "changePercent": 0.678,
                                          "previousClose": 331.34,
                                          "marketState": "unknown",
                                          "asOf": "2026-09-16T14:02:09Z"
                                        }
                                      }
                                    ]
                                  }
                                }
                                """)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(
                        List.of("SATYAM.BO", "AAPL")
                );

        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).symbol());
    }

    @Test
    void getQuotesMapsMultipleQuotes()
         {

        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody("""
                                {
                                  "data": {
                                    "quotes": [
                                      {
                                        "symbol": "AAPL",
                                        "stale": false,
                                        "quote": {
                                          "symbol": "AAPL",
                                          "price": 333.588,
                                          "currency": "USD",
                                          "change": 2.248,
                                          "changePercent": 0.678,
                                          "previousClose": 331.34,
                                          "marketState": "unknown",
                                          "asOf": "2026-09-16T14:02:09Z"
                                        }
                                      },
                                      {
                                        "symbol": "MSFT",
                                        "stale": true,
                                        "quote": {
                                          "symbol": "MSFT",
                                          "price": 500.00,
                                          "currency": "USD",
                                          "change": 1.20,
                                          "changePercent": 0.24,
                                          "previousClose": 498.80,
                                          "marketState": "closed",
                                          "asOf": "2026-09-16T14:02:09Z"
                                        }
                                      }
                                    ]
                                  }
                                }
                                """)
        );

        FauxnanceWebClient client =
                new FauxnanceWebClient(
                        server.url("/").toString(),
                        "/quotes",
                        "key"
                );

        List<Quote> result =
                client.getQuotes(
                        List.of("AAPL", "MSFT")
                );

        assertEquals(2, result.size());

        assertEquals(
                List.of("AAPL", "MSFT"),
                result.stream()
                        .map(Quote::symbol)
                        .toList()
        );

        assertFalse(result.get(0).stale());
        assertTrue(result.get(1).stale());
    }
}
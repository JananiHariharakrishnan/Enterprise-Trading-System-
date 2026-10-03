package com.tradingsystem.service.impl;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.messaging.QuotePayload;
import com.tradingsystem.service.Impl.MarketDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarketDataServiceImpl.
 *
 * Tests:
 * - Valid quote processing
 * - Full quote data retention
 * - Multiple symbol caching
 * - Price updates
 * - Idempotency
 * - Event type validation
 * - Payload validation
 * - Cache retrieval
 * - Concurrent access
 * - Cache integrity
 * - Cache clearing
 */
@ExtendWith(MockitoExtension.class)
class MarketDataServiceImplTest {

    private MarketDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketDataServiceImpl();
    }

    // ============================================================
    // Valid Quote Processing
    // ============================================================

    @Test
    void processQuote_validQuote_updatesCache() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                new BigDecimal("0.21"),
                new BigDecimal("0.09"),
                new BigDecimal("232.50"),
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote
        );

        LatestMarketPrice cached =
                service.getLatestPrice("AAPL");

        assertNotNull(cached);

        assertEquals(
                "AAPL",
                cached.getSymbol()
        );

        assertEquals(
                new BigDecimal("232.71"),
                cached.getPrice()
        );

        assertEquals(
                "USD",
                cached.getCurrency()
        );

        assertEquals(
                new BigDecimal("0.21"),
                cached.getChange()
        );

        assertEquals(
                new BigDecimal("0.09"),
                cached.getChangePercent()
        );

        assertEquals(
                new BigDecimal("232.50"),
                cached.getPreviousClose()
        );

        assertEquals(
                "open",
                cached.getMarketState()
        );

        assertFalse(cached.getStale());

        assertEquals(
                "2026-09-28T09:14:58Z",
                cached.getQuoteAsOf()
        );
    }

    @Test
    void processQuote_fullQuoteData_preservesAllFields() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("329.40"),
                "USD",
                new BigDecimal("-9.00"),
                new BigDecimal("-2.6595744680851063"),
                new BigDecimal("338.40"),
                "unknown",
                false,
                "2026-09-29T20:00:00Z"
        );

        service.processQuote(
                "event-full-quote",
                "QUOTE",
                quote
        );

        LatestMarketPrice cached =
                service.getLatestPrice("AAPL");

        assertNotNull(cached);

        /*
         * Verify that none of the useful market-data fields
         * were discarded when converting QuotePayload into
         * LatestMarketPrice.
         */
        assertEquals("AAPL", cached.getSymbol());
        assertEquals(new BigDecimal("329.40"), cached.getPrice());
        assertEquals("USD", cached.getCurrency());
        assertEquals(new BigDecimal("-9.00"), cached.getChange());
        assertEquals(
                new BigDecimal("-2.6595744680851063"),
                cached.getChangePercent()
        );
        assertEquals(
                new BigDecimal("338.40"),
                cached.getPreviousClose()
        );
        assertEquals("unknown", cached.getMarketState());
        assertFalse(cached.getStale());
        assertEquals(
                "2026-09-29T20:00:00Z",
                cached.getQuoteAsOf()
        );
    }

    @Test
    void processQuote_multipleSymbols_cachesIndependently() {

        QuotePayload appleQuote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        QuotePayload googleQuote = new QuotePayload(
                "GOOGL",
                new BigDecimal("139.25"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:59Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                appleQuote
        );

        service.processQuote(
                "event-2",
                "QUOTE",
                googleQuote
        );

        LatestMarketPrice apple =
                service.getLatestPrice("AAPL");

        LatestMarketPrice google =
                service.getLatestPrice("GOOGL");

        assertNotNull(apple);
        assertEquals("AAPL", apple.getSymbol());
        assertEquals(
                new BigDecimal("232.71"),
                apple.getPrice()
        );

        assertNotNull(google);
        assertEquals("GOOGL", google.getSymbol());
        assertEquals(
                new BigDecimal("139.25"),
                google.getPrice()
        );
    }

    @Test
    void processQuote_priceUpdates_overwritesPreviousPrice() {

        QuotePayload quote1 = new QuotePayload(
                "AAPL",
                new BigDecimal("230.00"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:00:00Z"
        );

        QuotePayload quote2 = new QuotePayload(
                "AAPL",
                new BigDecimal("235.00"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:15:00Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote1
        );

        LatestMarketPrice first =
                service.getLatestPrice("AAPL");

        assertEquals(
                new BigDecimal("230.00"),
                first.getPrice()
        );

        service.processQuote(
                "event-2",
                "QUOTE",
                quote2
        );

        LatestMarketPrice second =
                service.getLatestPrice("AAPL");

        assertEquals(
                new BigDecimal("235.00"),
                second.getPrice()
        );

        assertEquals(
                "2026-09-28T09:15:00Z",
                second.getQuoteAsOf()
        );
    }

    // ============================================================
    // Idempotency Tests
    // ============================================================

    @Test
    void processQuote_duplicateEventId_ignoresDuplicate() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote
        );

        assertTrue(
                service.isEventProcessed("event-1")
        );

        QuotePayload differentQuote = new QuotePayload(
                "AAPL",
                new BigDecimal("250.00"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T10:00:00Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                differentQuote
        );

        LatestMarketPrice cached =
                service.getLatestPrice("AAPL");

        assertNotNull(cached);

        assertEquals(
                new BigDecimal("232.71"),
                cached.getPrice()
        );
    }

    @Test
    void processQuote_duplicateEventIdWithDifferentSymbol_ignoresSecondEvent() {

        QuotePayload quote1 = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        QuotePayload quote2 = new QuotePayload(
                "GOOGL",
                new BigDecimal("139.25"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote1
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote2
        );

        assertNotNull(
                service.getLatestPrice("AAPL")
        );

        assertNull(
                service.getLatestPrice("GOOGL")
        );
    }

    @Test
    void processQuote_duplicateEventId_doesNotChangeOtherCachedSymbols() {

        QuotePayload appleQuote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        QuotePayload googleQuote = new QuotePayload(
                "GOOGL",
                new BigDecimal("139.25"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:59Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                appleQuote
        );

        service.processQuote(
                "event-2",
                "QUOTE",
                googleQuote
        );

        // Duplicate event-1 with different price
        QuotePayload duplicateApple = new QuotePayload(
                "AAPL",
                new BigDecimal("999.99"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T10:00:00Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                duplicateApple
        );

        assertEquals(
                new BigDecimal("232.71"),
                service.getLatestPrice("AAPL").getPrice()
        );

        assertEquals(
                new BigDecimal("139.25"),
                service.getLatestPrice("GOOGL").getPrice()
        );
    }

    // ============================================================
    // Invalid Event Type Tests
    // ============================================================

    @Test
    void processQuote_invalidEventType_throwsException() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.processQuote(
                                "event-1",
                                "ORDER_PLACED",
                                quote
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("Invalid eventType")
        );

        assertTrue(
                exception.getMessage()
                        .contains("QUOTE")
        );

        assertTrue(
                exception.getMessage()
                        .contains("ORDER_PLACED")
        );
    }

    @Test
    void processQuote_unknownEventType_throwsException() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.processQuote(
                        "event-1",
                        "UNKNOWN_TYPE",
                        quote
                )
        );
    }

    @Test
    void processQuote_nullEventType_throwsException() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.processQuote(
                        "event-1",
                        null,
                        quote
                )
        );
    }

    // ============================================================
    // Malformed Payload Tests
    // ============================================================

    @Test
    void processQuote_nullPayload_throwsException() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.processQuote(
                                "event-1",
                                "QUOTE",
                                null
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("Quote payload cannot be null")
        );
    }

    @Test
    void processQuote_nullSymbol_throwsException() {

        QuotePayload quote = new QuotePayload(
                null,
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.processQuote(
                                "event-1",
                                "QUOTE",
                                quote
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("symbol")
        );

        assertTrue(
                exception.getMessage()
                        .contains("null or empty")
        );
    }

    @Test
    void processQuote_blankSymbol_throwsException() {

        QuotePayload quote = new QuotePayload(
                "   ",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.processQuote(
                                "event-1",
                                "QUOTE",
                                quote
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("symbol")
        );

        assertTrue(
                exception.getMessage()
                        .contains("null or empty")
        );
    }

    @Test
    void processQuote_nullPrice_throwsException() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                null,
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.processQuote(
                                "event-1",
                                "QUOTE",
                                quote
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("price cannot be null")
        );

        assertTrue(
                exception.getMessage()
                        .contains("AAPL")
        );
    }

    // ============================================================
    // Cache Retrieval Tests
    // ============================================================

    @Test
    void getLatestPrice_symbolNotCached_returnsNull() {

        assertNull(
                service.getLatestPrice("UNKNOWN")
        );
    }

    @Test
    void getLatestPrice_afterProcessing_returnsCachedPrice() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote
        );

        LatestMarketPrice price =
                service.getLatestPrice("AAPL");

        assertNotNull(price);

        assertEquals(
                new BigDecimal("232.71"),
                price.getPrice()
        );
    }

    @Test
    void getLatestPrice_returnsFullCachedQuote() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("329.40"),
                "USD",
                new BigDecimal("-9.00"),
                new BigDecimal("-2.6595744680851063"),
                new BigDecimal("338.40"),
                "unknown",
                false,
                "2026-09-29T20:00:00Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote
        );

        LatestMarketPrice price =
                service.getLatestPrice("AAPL");

        assertNotNull(price);

        assertEquals("AAPL", price.getSymbol());
        assertEquals(
                new BigDecimal("329.40"),
                price.getPrice()
        );
        assertEquals("USD", price.getCurrency());
        assertEquals(
                new BigDecimal("-9.00"),
                price.getChange()
        );
        assertEquals(
                new BigDecimal("-2.6595744680851063"),
                price.getChangePercent()
        );
        assertEquals(
                new BigDecimal("338.40"),
                price.getPreviousClose()
        );
        assertEquals("unknown", price.getMarketState());
        assertFalse(price.getStale());
        assertEquals(
                "2026-09-29T20:00:00Z",
                price.getQuoteAsOf()
        );
    }

    // ============================================================
    // Concurrent Access Tests
    // ============================================================

    @Test
    void processQuote_concurrentWrites_handlesThreadSafety()
            throws InterruptedException {

        QuotePayload quote1 = new QuotePayload(
                "AAPL",
                new BigDecimal("230.00"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:00:00Z"
        );

        QuotePayload quote2 = new QuotePayload(
                "AAPL",
                new BigDecimal("235.00"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:15:00Z"
        );

        Thread t1 =
                new Thread(
                        () -> service.processQuote(
                                "event-1",
                                "QUOTE",
                                quote1
                        )
                );

        Thread t2 =
                new Thread(
                        () -> service.processQuote(
                                "event-2",
                                "QUOTE",
                                quote2
                        )
                );

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        LatestMarketPrice price =
                service.getLatestPrice("AAPL");

        assertNotNull(price);

        /*
         * Both writes are valid. Because thread scheduling is
         * nondeterministic, either quote may be the final value.
         */
        assertTrue(
                price.getPrice().equals(
                        new BigDecimal("230.00")
                )
                        || price.getPrice().equals(
                        new BigDecimal("235.00")
                )
        );
    }

    // ============================================================
    // Cache Integrity Tests
    // ============================================================

    @Test
    void getAllCachedPrices_returnsAllCachedSymbols() {

        QuotePayload appleQuote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        QuotePayload googleQuote = new QuotePayload(
                "GOOGL",
                new BigDecimal("139.25"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:59Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                appleQuote
        );

        service.processQuote(
                "event-2",
                "QUOTE",
                googleQuote
        );

        Map<String, LatestMarketPrice> cachedPrices =
                service.getAllCachedPrices();

        assertEquals(2, cachedPrices.size());

        assertTrue(
                cachedPrices.containsKey("AAPL")
        );

        assertTrue(
                cachedPrices.containsKey("GOOGL")
        );

        assertEquals(
                new BigDecimal("232.71"),
                cachedPrices.get("AAPL").getPrice()
        );

        assertEquals(
                new BigDecimal("139.25"),
                cachedPrices.get("GOOGL").getPrice()
        );
    }

    @Test
    void getAllCachedPrices_returnsUnmodifiableMap() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote
        );

        Map<String, LatestMarketPrice> cachedPrices =
                service.getAllCachedPrices();

        assertEquals(
                1,
                cachedPrices.size()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> cachedPrices.put(
                        "GOOGL",
                        new LatestMarketPrice()
                )
        );
    }

    // ============================================================
    // Cache Clearing Tests
    // ============================================================

    @Test
    void clearCache_clearsAllPricesAndEvents() {

        QuotePayload quote = new QuotePayload(
                "AAPL",
                new BigDecimal("232.71"),
                "USD",
                null,
                null,
                null,
                "open",
                false,
                "2026-09-28T09:14:58Z"
        );

        service.processQuote(
                "event-1",
                "QUOTE",
                quote
        );

        assertNotNull(
                service.getLatestPrice("AAPL")
        );

        assertTrue(
                service.isEventProcessed("event-1")
        );

        service.clearCache();

        assertNull(
                service.getLatestPrice("AAPL")
        );

        assertFalse(
                service.isEventProcessed("event-1")
        );

        assertTrue(
                service.getAllCachedPrices().isEmpty()
        );
    }
}
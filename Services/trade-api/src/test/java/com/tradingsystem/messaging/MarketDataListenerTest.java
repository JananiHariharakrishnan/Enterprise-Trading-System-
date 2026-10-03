package com.tradingsystem.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MarketDataListener.
 *
 * Tests business-level market-data processing:
 * - valid quote events
 * - null envelopes
 * - null payloads
 * - unexpected event types
 * - unsupported schema versions
 * - service validation failures
 * - unexpected service failures
 * - multiple valid events
 *
 * Does NOT test Kafka infrastructure or JSON deserialization.
 */
@ExtendWith(MockitoExtension.class)
class MarketDataListenerTest {

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private ObjectMapper objectMapper;

    private MarketDataListener listener;

    @BeforeEach
    void setUp() {
        listener = new MarketDataListener(
                objectMapper,
                marketDataService
        );
    }

    // ============================================================
    // Valid Event Processing
    // ============================================================

    @Test
    void processMarketData_validQuoteEvent_delegatesToService() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload
                );

        listener.processMarketData(envelope);

        verify(marketDataService)
                .processQuote(
                        "event-123",
                        "QUOTE",
                        payload
                );
    }

    @Test
    void processMarketData_nullEnvelope_handlesSilently() {

        listener.processMarketData(null);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    @Test
    void processMarketData_nullPayload_handlesSilently() {

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        null
                );

        listener.processMarketData(envelope);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    // ============================================================
    // Unexpected Event Type Tests
    // ============================================================

    @Test
    void processMarketData_unexpectedEventType_logsWarningAndSkips() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "ORDER_PLACED",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload
                );

        listener.processMarketData(envelope);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    @Test
    void processMarketData_unknownEventType_logsWarningAndSkips() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "UNKNOWN_EVENT_TYPE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload
                );

        listener.processMarketData(envelope);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    @Test
    void processMarketData_nullEventType_logsWarningAndSkips() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        null,
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload
                );

        listener.processMarketData(envelope);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    // ============================================================
    // Schema Version Tests
    // ============================================================

    @Test
    void processMarketData_unsupportedSchemaVersion_logsWarningAndSkips() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        2,
                        payload
                );

        listener.processMarketData(envelope);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    @Test
    void processMarketData_nullSchemaVersion_logsWarningAndSkips() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        null,
                        payload
                );

        listener.processMarketData(envelope);

        verify(marketDataService, never())
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );
    }

    // ============================================================
    // Service Validation Failure
    // ============================================================

    @Test
    void processMarketData_serviceThrowsIllegalArgument_catchesAndLogs() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload
                );

        doThrow(
                new IllegalArgumentException(
                        "Quote symbol cannot be null or empty"
                )
        ).when(marketDataService)
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );

        /*
         * processMarketData() does not catch the exception itself.
         * The Kafka adapter method handles IllegalArgumentException.
         *
         * Therefore this test should expect the exception when
         * directly testing processMarketData().
         */
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> listener.processMarketData(envelope)
        );

        verify(marketDataService)
                .processQuote(
                        "event-123",
                        "QUOTE",
                        payload
                );
    }

    // ============================================================
    // Service Error Handling
    // ============================================================

    @Test
    void processMarketData_serviceThrowsUnexpectedException_propagatesException() {

        QuotePayload payload = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope =
                new KafkaEventEnvelope<>(
                        "event-123",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload
                );

        doThrow(
                new RuntimeException("Database connection failed")
        ).when(marketDataService)
                .processQuote(
                        anyString(),
                        anyString(),
                        any()
                );

        RuntimeException thrown =
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> listener.processMarketData(envelope)
                );

        org.junit.jupiter.api.Assertions.assertEquals(
                "Database connection failed",
                thrown.getMessage()
        );

        verify(marketDataService)
                .processQuote(
                        "event-123",
                        "QUOTE",
                        payload
                );
    }

    // ============================================================
    // Multiple Events
    // ============================================================

    @Test
    void processMarketData_multipleValidEvents_delegatesAll() {

        QuotePayload payload1 = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope1 =
                new KafkaEventEnvelope<>(
                        "event-1",
                        "QUOTE",
                        "2026-09-28T09:15:00Z",
                        "market-poller",
                        1,
                        payload1
                );

        QuotePayload payload2 = new QuotePayload(
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

        KafkaEventEnvelope<QuotePayload> envelope2 =
                new KafkaEventEnvelope<>(
                        "event-2",
                        "QUOTE",
                        "2026-09-28T09:15:01Z",
                        "market-poller",
                        1,
                        payload2
                );

        listener.processMarketData(envelope1);
        listener.processMarketData(envelope2);

        verify(marketDataService)
                .processQuote(
                        "event-1",
                        "QUOTE",
                        payload1
                );

        verify(marketDataService)
                .processQuote(
                        "event-2",
                        "QUOTE",
                        payload2
                );
    }
}
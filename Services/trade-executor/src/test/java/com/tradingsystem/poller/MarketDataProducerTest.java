package com.tradingsystem.poller;

import com.tradingsystem.event.KafkaEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataProducerTest {

    @Mock
    private KafkaTemplate<String, ?> kafkaTemplate;

    @Test
    void publishQuoteSendsMessageToMarketDataTopic() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        Quote quote = quote("AAPL");

        producer.publishQuote(quote);

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("AAPL"),
                any()
        );
    }

    @Test
    void publishQuoteUsesSymbolAsKafkaKey() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        Quote quote = quote("MSFT");

        producer.publishQuote(quote);

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("MSFT"),
                any()
        );
    }

    @Test
    void publishQuoteCreatesKafkaEventWithCorrectMetadataAndPayload() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        Quote quote = quote("TSLA");

        producer.publishQuote(quote);

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("TSLA"),
                argThat(event -> {

                    assertInstanceOf(
                            KafkaEvent.class,
                            event
                    );

                    KafkaEvent<?> kafkaEvent =
                            (KafkaEvent<?>) event;

                    assertEquals(
                            "QUOTE",
                            kafkaEvent.getEventType()
                    );

                    assertEquals(
                            "market-poller",
                            kafkaEvent.getSource()
                    );

                    assertEquals(
                            1,
                            kafkaEvent.getSchemaVersion()
                    );

                    assertEquals(
                            quote,
                            kafkaEvent.getPayload()
                    );

                    return true;
                })
        );
    }

    @Test
    void publishQuoteCreatesEventId() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        producer.publishQuote(quote("AAPL"));

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("AAPL"),
                argThat(event -> {

                    assertInstanceOf(
                            KafkaEvent.class,
                            event
                    );

                    KafkaEvent<?> kafkaEvent =
                            (KafkaEvent<?>) event;

                    assertNotNull(
                            kafkaEvent.getEventId()
                    );

                    assertFalse(
                            kafkaEvent.getEventId().isBlank()
                    );

                    return true;
                })
        );
    }

    @Test
    void publishQuoteCreatesEventTime() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        producer.publishQuote(quote("AAPL"));

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("AAPL"),
                argThat(event -> {

                    assertInstanceOf(
                            KafkaEvent.class,
                            event
                    );

                    KafkaEvent<?> kafkaEvent =
                            (KafkaEvent<?>) event;

                    assertNotNull(
                            kafkaEvent.getEventTime()
                    );

                    assertFalse(
                            kafkaEvent.getEventTime().isBlank()
                    );

                    return true;
                })
        );
    }

    @Test
    void publishQuoteCreatesSeparateMessageForEachQuote() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        Quote aapl = quote("AAPL");
        Quote msft = quote("MSFT");
        Quote tsla = quote("TSLA");

        producer.publishQuote(aapl);
        producer.publishQuote(msft);
        producer.publishQuote(tsla);

        verify(
                kafkaTemplate,
                times(3)
        ).send(
                eq("market-data"),
                anyString(),
                any()
        );
    }

    @Test
    void publishQuoteUsesCorrectKeyForEachSymbol() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        producer.publishQuote(quote("AAPL"));
        producer.publishQuote(quote("MSFT"));
        producer.publishQuote(quote("TSLA"));

        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(
                kafkaTemplate,
                times(3)
        ).send(
                eq("market-data"),
                keyCaptor.capture(),
                any()
        );

        assertEquals(
                3,
                keyCaptor.getAllValues().size()
        );

        assertEquals(
                "AAPL",
                keyCaptor.getAllValues().get(0)
        );

        assertEquals(
                "MSFT",
                keyCaptor.getAllValues().get(1)
        );

        assertEquals(
                "TSLA",
                keyCaptor.getAllValues().get(2)
        );
    }

    @Test
    void publishQuotePreservesCompleteQuoteAsPayload() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        Quote quote =
                new Quote(
                        "AAPL",
                        BigDecimal.valueOf(333.588),
                        "USD",
                        BigDecimal.valueOf(2.248),
                        BigDecimal.valueOf(0.678457),
                        BigDecimal.valueOf(331.34),
                        "unknown",
                        false,
                        Instant.parse(
                                "2026-09-16T14:02:09Z"
                        )
                );

        producer.publishQuote(quote);

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("AAPL"),
                argThat(event -> {

                    assertInstanceOf(
                            KafkaEvent.class,
                            event
                    );

                    KafkaEvent<?> kafkaEvent =
                            (KafkaEvent<?>) event;

                    assertEquals(
                            quote,
                            kafkaEvent.getPayload()
                    );

                    Quote payload =
                            (Quote) kafkaEvent.getPayload();

                    assertEquals(
                            "AAPL",
                            payload.symbol()
                    );

                    assertEquals(
                            BigDecimal.valueOf(333.588),
                            payload.price()
                    );

                    assertEquals(
                            "USD",
                            payload.currency()
                    );

                    assertEquals(
                            BigDecimal.valueOf(2.248),
                            payload.change()
                    );

                    assertEquals(
                            BigDecimal.valueOf(0.678457),
                            payload.changePercent()
                    );

                    assertEquals(
                            BigDecimal.valueOf(331.34),
                            payload.previousClose()
                    );

                    assertEquals(
                            "unknown",
                            payload.marketState()
                    );

                    assertFalse(
                            payload.stale()
                    );

                    assertEquals(
                            Instant.parse(
                                    "2026-09-16T14:02:09Z"
                            ),
                            payload.quoteAsOf()
                    );

                    return true;
                })
        );
    }

    @Test
    void publishQuoteCreatesUniqueEventIds() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        producer.publishQuote(quote("AAPL"));
        producer.publishQuote(quote("MSFT"));

        verify(
                kafkaTemplate,
                times(2)
        ).send(
                eq("market-data"),
                anyString(),
                argThat(event -> {
                    assertInstanceOf(KafkaEvent.class, event);

                    KafkaEvent<?> kafkaEvent =
                            (KafkaEvent<?>) event;

                    assertNotNull(kafkaEvent.getEventId());
                    assertFalse(kafkaEvent.getEventId().isBlank());

                    return true;
                })
        );
    }

    @Test
    void publishQuoteUsesUtcEventTime() {

        MarketDataProducer producer =
                new MarketDataProducer(kafkaTemplate);

        producer.publishQuote(quote("AAPL"));

        verify(kafkaTemplate).send(
                eq("market-data"),
                eq("AAPL"),
                argThat(event -> {

                    assertInstanceOf(
                            KafkaEvent.class,
                            event
                    );

                    KafkaEvent<?> kafkaEvent =
                            (KafkaEvent<?>) event;

                    assertNotNull(
                            kafkaEvent.getEventTime()
                    );

                    assertFalse(
                            kafkaEvent.getEventTime().isBlank()
                    );

                    assertDoesNotThrow(
                            () -> Instant.parse(
                                    kafkaEvent.getEventTime()
                            )
                    );

                    return true;
                })
        );
    }

    private Quote quote(String symbol) {

        return new Quote(
                symbol,
                BigDecimal.valueOf(333.588),
                "USD",
                BigDecimal.valueOf(2.248),
                BigDecimal.valueOf(0.678),
                BigDecimal.valueOf(331.34),
                "unknown",
                false,
                Instant.parse(
                        "2026-09-16T14:02:09Z"
                )
        );
    }
}
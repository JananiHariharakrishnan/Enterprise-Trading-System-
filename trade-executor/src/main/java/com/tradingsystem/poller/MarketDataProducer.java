package com.tradingsystem.poller;

import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.poller.Quote;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class MarketDataProducer {

    private static final String MARKET_DATA_TOPIC = "market-data";

    private final KafkaTemplate<String, ?> kafkaTemplate;

    public MarketDataProducer(
            KafkaTemplate<String, ?> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishQuote(Quote quote) {

        KafkaEvent<Quote> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                "QUOTE",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "market-poller",
                1,
                quote
        );

        ((KafkaTemplate) kafkaTemplate).send(
                MARKET_DATA_TOPIC,
                quote.symbol(),
                event
        );
    }
}
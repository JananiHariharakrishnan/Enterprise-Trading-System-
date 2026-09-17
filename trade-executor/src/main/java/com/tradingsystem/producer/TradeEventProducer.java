package com.tradingsystem.producer;

import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.TradeEventPayload;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class TradeEventProducer {

    private static final String TRADE_EVENTS_TOPIC = "trade-events";
    private static final String SOURCE = "trade-executor";
    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, KafkaEvent<TradeEventPayload>> kafkaTemplate;

    public TradeEventProducer(
            KafkaTemplate<String, KafkaEvent<TradeEventPayload>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderFilled(TradeEventPayload payload) {
        publish("ORDER_FILLED", payload);
    }

    public void publishOrderRejected(TradeEventPayload payload) {
        publish("ORDER_REJECTED", payload);
    }

    private void publish(
            String eventType,
            TradeEventPayload payload
    ){

        KafkaEvent<TradeEventPayload> event =
                new KafkaEvent<>(
                        UUID.randomUUID().toString(),
                        eventType,
                        OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        SOURCE,
                        SCHEMA_VERSION,
                        payload
                );


        kafkaTemplate.send(
                TRADE_EVENTS_TOPIC,
                String.valueOf(payload.getAccountId()),
                event
        );


        System.out.println(
                "Published event: " + eventType
        );

    }
}

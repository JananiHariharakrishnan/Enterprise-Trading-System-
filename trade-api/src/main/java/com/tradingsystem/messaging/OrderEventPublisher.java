package com.tradingsystem.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class OrderEventPublisher {

    private static final String EVENT_TYPE = "ORDER_PLACED";
    private static final String SOURCE = "trade-api";
    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String ordersTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.orders-topic:orders}") String ordersTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersTopic = ordersTopic;
    }

    public void publishOrderPlaced(OrderPlacedEvent event) {

        try {

            OrderPlacedPayload payload =
                    new OrderPlacedPayload(
                            event.accountId(),
                            event.symbol(),
                            event.side(),
                            event.orderType(),
                            event.quantity(),
                            event.price(),
                            event.idempotencyKey(),
                            event.createdOn().toString()
                    );

            KafkaEventEnvelope<OrderPlacedPayload> kafkaEvent =
                    new KafkaEventEnvelope<>(
                            UUID.randomUUID().toString(),
                            EVENT_TYPE,
                            OffsetDateTime.now(ZoneOffset.UTC).toString(),
                            SOURCE,
                            SCHEMA_VERSION,
                            payload
                    );

            String message =
                    objectMapper.writeValueAsString(kafkaEvent);

            kafkaTemplate.send(
                    ordersTopic,
                    String.valueOf(event.accountId()),
                    message
            );

        } catch (JsonProcessingException ex) {

            throw new IllegalStateException(
                    "Unable to serialize order event",
                    ex
            );
        }
    }
}
package com.tradingsystem.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Publishes ORDER_FILLED / ORDER_REJECTED events to the `trade-events` topic.
 * The caller should register a TransactionSynchronization to publish this after commit.
 */
@Component
public class TradeEventPublisher {

    private static final String SOURCE = "trade-api";
    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String tradeEventsTopic;

    public TradeEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.trade-events-topic:trade-events}") String tradeEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tradeEventsTopic = tradeEventsTopic;
    }

    public void publishTradeEvent(TradeEventPayload payload, String eventType) {
        try {
            var envelope = new KafkaEventEnvelope<TradeEventPayload>(
                    UUID.randomUUID().toString(),
                    eventType,
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    SOURCE,
                    SCHEMA_VERSION,
                    payload
            );

            String message = objectMapper.writeValueAsString(envelope);

            kafkaTemplate.send(
                    tradeEventsTopic,
                    String.valueOf(payload.getAccountId()),
                    message
            );

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize trade event", ex);
        }
    }
}
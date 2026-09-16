package com.tradingsystem.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OrderEventPublisher.class);

    private static final String EVENT_TYPE_ORDER_PLACED = "ORDER_PLACED";
    private static final String SOURCE_TRADE_API = "trade-api";
    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String ordersTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.orders-topic:orders}") String ordersTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        // Create a dedicated ObjectMapper for this component so we don't depend on an application-provided bean.
        // findAndRegisterModules() will register available modules (e.g. JavaTime) if present on the classpath.
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.ordersTopic = ordersTopic;
    }

    public void publishOrderPlaced(OrderPlacedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "OrderPlacedEvent must not be null"
            );
        }

        if (event.orderId() == null) {
            throw new IllegalArgumentException(
                    "Order ID must not be null"
            );
        }

        if (event.accountId() == null) {
            throw new IllegalArgumentException(
                    "Account ID must not be null"
            );
        }

        KafkaEventEnvelope<OrderPlacedEvent> envelope =
                new KafkaEventEnvelope<>(
                        UUID.randomUUID(),
                        EVENT_TYPE_ORDER_PLACED,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        SOURCE_TRADE_API,
                        SCHEMA_VERSION,
                        event
                );

        final String eventMessage;

        try {
            eventMessage = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            log.error(
                    "Failed to serialize ORDER_PLACED event. orderId={}",
                    event.orderId(),
                    exception
            );

            throw new IllegalStateException(
                    "Failed to serialize order event",
                    exception
            );
        }

        /*
         * The Kafka key is accountId, not orderId.
         * This guarantees ordering for orders belonging to the same account.
         */
        String messageKey = event.accountId().toString();

        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(
                        ordersTopic,
                        messageKey,
                        eventMessage
                );

        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error(
                        "Failed to publish ORDER_PLACED event. " +
                                "topic={}, accountId={}, orderId={}",
                        ordersTopic,
                        event.accountId(),
                        event.orderId(),
                        exception
                );
                return;
            }

            if (result != null && result.getRecordMetadata() != null) {
                log.info(
                        "ORDER_PLACED event published. " +
                                "topic={}, partition={}, offset={}, accountId={}, orderId={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.accountId(),
                        event.orderId()
                );
            }
        });
    }
}
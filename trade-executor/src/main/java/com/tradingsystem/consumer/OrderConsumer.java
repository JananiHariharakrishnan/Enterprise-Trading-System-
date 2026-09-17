package com.tradingsystem.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.execution.ExecutionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for ORDER_PLACED events.
 * 
 * Implements robust error handling with failure classification:
 * - PERMANENT FAILURES: malformed JSON, missing fields, order not found → send to DLT
 * - TRANSIENT FAILURES: DB connection issues, broker unavailable → nack (Kafka retries)
 * - SPECIAL CASE: Fauxnance unavailable → reject order, acknowledge (processed)
 */
@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    private static final String DLT_TOPIC = "orders.DLT";

    private final ObjectMapper objectMapper;
    private final ExecutionService executionService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderConsumer(
            ObjectMapper objectMapper,
            ExecutionService executionService,
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.objectMapper = objectMapper;
        this.executionService = executionService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "orders",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrder(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String message = record.value();
        String key = record.key();
        int partition = record.partition();
        long offset = record.offset();

        try {
            // LEVEL 1: Validate JSON parsing
            JsonNode root;
            try {
                root = objectMapper.readTree(message);
            } catch (JsonProcessingException e) {
                sendToDLT(record, "malformed_json", "JSON parsing failed: " + e.getMessage());
                acknowledgment.acknowledge();
                return;
            }

            // LEVEL 2: Extract and validate event
            KafkaEvent<OrderPlacedPayload> event;
            try {
                if (root.has("payload")) {
                    // message is envelope
                    event = objectMapper.readValue(
                            message,
                            new TypeReference<KafkaEvent<OrderPlacedPayload>>() {}
                    );
                } else {
                    // raw payload (legacy format)
                    OrderPlacedPayload payload = objectMapper.readValue(message, OrderPlacedPayload.class);
                    event = new KafkaEvent<>();
                    event.setPayload(payload);
                }
            } catch (JsonProcessingException e) {
                sendToDLT(record, "invalid_event_format", "Event deserialization failed: " + e.getMessage());
                acknowledgment.acknowledge();
                return;
            }

            if (event == null || event.getPayload() == null) {
                sendToDLT(record, "missing_payload", "Event or payload is null");
                acknowledgment.acknowledge();
                return;
            }

            // LEVEL 3: Validate orderId
            String orderId = event.getPayload().getOrderId();
            if (orderId == null || orderId.isBlank()) {
                sendToDLT(record, "missing_order_id", "orderId is null or blank");
                acknowledgment.acknowledge();
                return;
            }

            // Validate orderId is UUID format
            try {
                UUID.fromString(orderId);
            } catch (IllegalArgumentException e) {
                sendToDLT(record, "invalid_order_id_format", "orderId is not a valid UUID: " + orderId);
                acknowledgment.acknowledge();
                return;
            }

            // LEVEL 4: Validate eventType
            String eventType = event.getEventType();
            if (eventType == null || !eventType.equals("ORDER_PLACED")) {
                sendToDLT(record, "unexpected_event_type", 
                    "Expected EVENT_TYPE=ORDER_PLACED, got: " + eventType);
                acknowledgment.acknowledge();
                return;
            }

            log.info("ORDER_PLACED received: orderId={} key={} partition={} offset={}",
                    orderId, key, partition, offset);

            // Set eventId on payload for idempotency tracking
            if (event.getEventId() != null) {
                event.getPayload().setEventId(event.getEventId());
            }

            // LEVEL 5: Execute order (permanent and transient errors distinguished here)
            try {
                executionService.execute(event.getPayload());
                // Success!
                acknowledgment.acknowledge();
                log.info("Order {} executed successfully", orderId);

            } catch (org.springframework.dao.DataAccessException dae) {
                // Database connection issue → transient failure
                log.warn("Database connection error, will retry: {}", dae.getMessage());
                throw dae; // Rethrow to trigger nack

            } catch (RuntimeException re) {
                String msg = buildExceptionMessage(re);

                if (isPermanentExecutionFailure(re)) {
                    sendToDLT(record, failureReasonFor(re), msg);
                    acknowledgment.acknowledge();
                } else if (isTransientFailure(msg)) {
                    log.warn("Transient error detected, will retry: {}", msg);
                    throw re; // Rethrow to trigger nack
                } else {
                    // Unknown error → treat as permanent
                    sendToDLT(record, "unexpected_error", msg);
                    acknowledgment.acknowledge();
                }
            }

        } catch (Exception e) {
            // Nack for transient failures (rethrown exceptions)
            log.error("Order processing failed (will retry by Kafka broker): {}", e.getMessage(), e);
            // Do NOT acknowledge; let Kafka retry
        }
    }

    /**
     * Send a message to the dead-letter topic for permanent failures.
     * 
     * The DLT message contains:
     * - value: original message
     * - headers: failure_reason, error_message, original_topic, timestamp
     */
    private void sendToDLT(ConsumerRecord<String, String> record, String reason, String errorMessage) {
        try {
            Headers headers = new RecordHeaders();
            headers.add("failure_reason", reason.getBytes());
            headers.add("error_message", errorMessage.getBytes());
            headers.add("original_topic", "orders".getBytes());
            headers.add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes());

            kafkaTemplate.send(DLT_TOPIC, record.key(), record.value()).get();

            log.error("Message sent to DLT: topic={}, reason={}, error={}; message={}", 
                DLT_TOPIC, reason, errorMessage, record.value());

        } catch (Exception e) {
            log.error("Failed to send message to DLT: {}", e.getMessage(), e);
        }
    }

    private boolean isPermanentExecutionFailure(RuntimeException exception) {
        String message = buildExceptionMessage(exception).toLowerCase();
        return message.contains("order not found")
                || message.contains("not found in database")
                || message.contains("malformed execution")
                || message.contains("status is required")
                || message.contains("executedprice is required")
                || message.contains("cannot sell without existing position")
                || message.contains("insufficient holdings")
                || message.contains("failed to update position after 3 retries")
                || message.contains("concurrent updates may be blocking this transaction");
    }

    private String failureReasonFor(RuntimeException exception) {
        String message = buildExceptionMessage(exception).toLowerCase();
        if (message.contains("order not found") || message.contains("not found in database")) {
            return "order_not_found";
        }
        if (message.contains("failed to update position after 3 retries")
                || message.contains("concurrent updates may be blocking this transaction")) {
            return "exhausted_lock_budget";
        }
        return "malformed_execution";
    }

    private boolean isTransientFailure(String message) {
        String normalizedMessage = message.toLowerCase();
        return normalizedMessage.contains("connection")
                || normalizedMessage.contains("timeout")
                || normalizedMessage.contains("unavailable")
                || normalizedMessage.contains("i/o error")
                || normalizedMessage.contains("failed to call trade-api")
                || normalizedMessage.contains("database");
    }

    private String buildExceptionMessage(Throwable throwable) {
        String currentMessage = throwable.getMessage();
        Throwable cause = throwable.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            if (currentMessage == null || currentMessage.isBlank()) {
                return cause.getMessage();
            }
            return currentMessage + ": " + cause.getMessage();
        }
        return currentMessage != null ? currentMessage : throwable.getClass().getSimpleName();
    }
}
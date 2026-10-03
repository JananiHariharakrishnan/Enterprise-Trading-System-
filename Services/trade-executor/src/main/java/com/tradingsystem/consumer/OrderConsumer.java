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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
    public void consumeOrder(
            ConsumerRecord<String, String> consumerRecord,
            Acknowledgment acknowledgment
    ) {
        String message = consumerRecord.value();
        String key = consumerRecord.key();
        int partition = consumerRecord.partition();
        long offset = consumerRecord.offset();

        try {
            JsonNode root = parseJson(consumerRecord, message, acknowledgment);
            if (root == null) {
                return;
            }

            KafkaEvent<OrderPlacedPayload> event =
                    deserializeEvent(consumerRecord, message, root, acknowledgment);

            if (event == null || event.getPayload() == null) {
                sendToDLT(
                        consumerRecord,
                        "missing_payload",
                        "Event or payload is null"
                );
                acknowledgment.acknowledge();
                return;
            }

            String orderId = event.getPayload().getOrderId();

            if (!validateOrderId(
                    consumerRecord,
                    orderId,
                    acknowledgment
            )) {
                return;
            }

            if (!validateEventType(
                    consumerRecord,
                    event,
                    acknowledgment
            )) {
                return;
            }

            log.info(
                    "ORDER_PLACED received: orderId={} key={} partition={} offset={}",
                    orderId,
                    key,
                    partition,
                    offset
            );

            setEventId(event);

            executeOrder(
                    consumerRecord,
                    event,
                    orderId,
                    acknowledgment
            );

        } catch (Exception exception) {
            log.error(
                    "Order processing failed (will retry by Kafka broker): {}",
                    exception.getMessage(),
                    exception
            );
        }
    }

    private JsonNode parseJson(
            ConsumerRecord<String, String> consumerRecord,
            String message,
            Acknowledgment acknowledgment
    ) {
        try {
            return objectMapper.readTree(message);
        } catch (JsonProcessingException exception) {
            sendToDLT(
                    consumerRecord,
                    "malformed_json",
                    "JSON parsing failed: " + exception.getMessage()
            );
            acknowledgment.acknowledge();
            return null;
        }
    }

    private KafkaEvent<OrderPlacedPayload> deserializeEvent(
            ConsumerRecord<String, String> consumerRecord,
            String message,
            JsonNode root,
            Acknowledgment acknowledgment
    ) {
        try {
            if (root.has("payload")) {
                return objectMapper.readValue(
                        message,
                        new TypeReference<KafkaEvent<OrderPlacedPayload>>() {
                        }
                );
            }

            OrderPlacedPayload payload =
                    objectMapper.readValue(message, OrderPlacedPayload.class);

            KafkaEvent<OrderPlacedPayload> event = new KafkaEvent<>();
            event.setPayload(payload);

            return event;

        } catch (JsonProcessingException exception) {
            sendToDLT(
                    consumerRecord,
                    "invalid_event_format",
                    "Event deserialization failed: " + exception.getMessage()
            );
            acknowledgment.acknowledge();
            return null;
        }
    }

    private boolean validateOrderId(
            ConsumerRecord<String, String> consumerRecord,
            String orderId,
            Acknowledgment acknowledgment
    ) {
        if (orderId == null || orderId.isBlank()) {
            sendToDLT(
                    consumerRecord,
                    "missing_order_id",
                    "orderId is null or blank"
            );
            acknowledgment.acknowledge();
            return false;
        }

        try {
            UUID.fromString(orderId);
            return true;
        } catch (IllegalArgumentException exception) {
            sendToDLT(
                    consumerRecord,
                    "invalid_order_id_format",
                    "orderId is not a valid UUID: " + orderId
            );
            acknowledgment.acknowledge();
            return false;
        }
    }

    private boolean validateEventType(
            ConsumerRecord<String, String> consumerRecord,
            KafkaEvent<OrderPlacedPayload> event,
            Acknowledgment acknowledgment
    ) {
        String eventType = event.getEventType();

        if (eventType == null || !eventType.equals("ORDER_PLACED")) {
            sendToDLT(
                    consumerRecord,
                    "unexpected_event_type",
                    "Expected EVENT_TYPE=ORDER_PLACED, got: " + eventType
            );
            acknowledgment.acknowledge();
            return false;
        }

        return true;
    }

    private void setEventId(KafkaEvent<OrderPlacedPayload> event) {
        if (event.getEventId() != null) {
            event.getPayload().setEventId(event.getEventId());
        }
    }

    private void executeOrder(
            ConsumerRecord<String, String> consumerRecord,
            KafkaEvent<OrderPlacedPayload> event,
            String orderId,
            Acknowledgment acknowledgment
    ) {
        try {
            executionService.execute(event.getPayload());

            acknowledgment.acknowledge();

            log.info(
                    "Order {} executed successfully",
                    orderId
            );

        } catch (org.springframework.dao.DataAccessException exception) {
            log.warn(
                    "Database connection error, will retry: {}",
                    exception.getMessage()
            );
            throw new RuntimeException(
                    "Failed to execute order " + orderId + " because of a database error",
                    exception
            );

        } catch (RuntimeException exception) {
            handleExecutionFailure(
                    consumerRecord,
                    exception,
                    acknowledgment
            );
        }
    }

    private void handleExecutionFailure(
            ConsumerRecord<String, String> consumerRecord,
            RuntimeException exception,
            Acknowledgment acknowledgment
    ) {
        String message = buildExceptionMessage(exception);

        if (isPermanentExecutionFailure(exception)) {
            sendToDLT(
                    consumerRecord,
                    failureReasonFor(exception),
                    message
            );
            acknowledgment.acknowledge();
            return;
        }

        if (isTransientFailure(message)) {
            log.warn(
                    "Transient error detected, will retry: {}",
                    message
            );
            throw exception;
        }

        sendToDLT(
                consumerRecord,
                "unexpected_error",
                message
        );
        acknowledgment.acknowledge();
    }

    private void sendToDLT(
            ConsumerRecord<String, String> consumerRecord,
            String reason,
            String errorMessage
    ) {
        try {
            Headers headers = new RecordHeaders();

            headers.add(
                    "failure_reason",
                    reason.getBytes(StandardCharsets.UTF_8)
            );
            headers.add(
                    "error_message",
                    errorMessage.getBytes(StandardCharsets.UTF_8)
            );
            headers.add(
                    "original_topic",
                    "orders".getBytes(StandardCharsets.UTF_8)
            );
            headers.add(
                    "timestamp",
                    String.valueOf(System.currentTimeMillis())
                            .getBytes(StandardCharsets.UTF_8)
            );

            kafkaTemplate
                    .send(
                            DLT_TOPIC,
                            consumerRecord.key(),
                            consumerRecord.value()
                    )
                    .get();

            log.error(
                    "Message sent to DLT: topic={}, reason={}, error={}; message={}",
                    DLT_TOPIC,
                    reason,
                    errorMessage,
                    consumerRecord.value()
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            log.error(
                    "Thread interrupted while sending message to DLT: reason={}, error={}",
                    reason,
                    errorMessage,
                    exception
            );

        } catch (Exception exception) {
            log.error(
                    "Failed to send message to DLT: topic={}, reason={}, error={}",
                    DLT_TOPIC,
                    reason,
                    errorMessage,
                    exception
            );
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
                || message.contains(
                "concurrent updates may be blocking this transaction"
        );
    }

    private String failureReasonFor(RuntimeException exception) {
        String message = buildExceptionMessage(exception).toLowerCase();

        if (message.contains("order not found")
                || message.contains("not found in database")) {
            return "order_not_found";
        }

        if (message.contains("failed to update position after 3 retries")
                || message.contains(
                "concurrent updates may be blocking this transaction"
        )) {
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

        if (cause != null
                && cause.getMessage() != null
                && !cause.getMessage().isBlank()) {

            if (currentMessage == null || currentMessage.isBlank()) {
                return cause.getMessage();
            }

            return currentMessage + ": " + cause.getMessage();
        }

        return currentMessage != null
                ? currentMessage
                : throwable.getClass().getSimpleName();
    }
}
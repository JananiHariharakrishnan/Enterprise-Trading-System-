package com.tradingsystem.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.model.User;
import com.tradingsystem.service.NotificationService;
import com.tradingsystem.service.UserService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that consumes order lifecycle events from the `trade-events` topic.
 * When an ORDER_FILLED event is detected, it looks up the user's email by account ID
 * from the database and sends a fill confirmation notification.
 */
@Component
public class TradeEventListener {

    private static final Logger log = LoggerFactory.getLogger(TradeEventListener.class);

    private static final String EVENT_TYPE_ORDER_FILLED = "ORDER_FILLED";
    private static final String STATUS_FILLED = "FILLED";

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final NotificationService notificationService;

    public TradeEventListener(
            ObjectMapper objectMapper,
            UserService userService,
            NotificationService notificationService
    ) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.trade-events-topic:trade-events}",
            groupId = "${app.kafka.notification-group-id:trade-notification-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTradeEvent(
            ConsumerRecord<String, String> consumerRecord,
            Acknowledgment acknowledgment
    ) {
        String message = consumerRecord != null ? consumerRecord.value() : null;

        try {
            processMessage(message);
        } catch (Exception ex) {
            log.error("Unexpected error in TradeEventListener: {}", ex.getMessage(), ex);
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    /**
     * Parse message and trigger notification if status is FILLED.
     * Separated for direct unit testing.
     */
    public void processMessage(String message) {
        if (message == null || message.isBlank()) {
            log.warn("Received empty trade event message");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message);
            TradeEventPayload payload;
            String eventType = null;

            if (root.has("payload")) {
                KafkaEventEnvelope<TradeEventPayload> envelope = objectMapper.readValue(
                        message,
                        new TypeReference<KafkaEventEnvelope<TradeEventPayload>>() {}
                );

                if (envelope == null || envelope.getPayload() == null) {
                    log.warn("Malformed envelope or missing payload in trade-events message");
                    return;
                }

                eventType = envelope.getEventType();
                payload = envelope.getPayload();
            } else {
                payload = objectMapper.readValue(message, TradeEventPayload.class);
            }

            if (payload == null) {
                log.warn("Payload is null in trade-events message");
                return;
            }

            processTradeEvent(eventType, payload);

        } catch (JsonProcessingException ex) {
            log.error("Failed to parse trade event message JSON: {}", ex.getMessage());
        }
    }

    void processTradeEvent(String eventType, TradeEventPayload payload) {
        boolean isFilled = EVENT_TYPE_ORDER_FILLED.equalsIgnoreCase(eventType)
                || STATUS_FILLED.equalsIgnoreCase(payload.getStatus());

        if (!isFilled) {
            log.debug("Ignoring non-FILLED trade event (eventType={}, status={})",
                    eventType, payload.getStatus());
            return;
        }

        Long accountId = payload.getAccountId();
        if (accountId == null) {
            log.warn("Cannot send email: trade event missing accountId for orderId={}", payload.getOrderId());
            return;
        }

        log.info("Processing ORDER_FILLED notification for orderId={} accountId={}",
                payload.getOrderId(), accountId);

        User user = userService.getUserByAccountId(accountId);
        if (user == null) {
            log.warn("No user found in user table for accountId={}", accountId);
            return;
        }

        notificationService.sendOrderFilledNotification(user, payload);
    }
}

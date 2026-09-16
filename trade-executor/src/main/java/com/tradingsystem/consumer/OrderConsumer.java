package com.tradingsystem.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.messaging.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(OrderConsumer.class);

    private final ObjectMapper objectMapper;

    public OrderConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "orders",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeOrder(
            String message,
            Acknowledgment acknowledgment) {

        try {
            OrderPlacedEvent event =
                    objectMapper.readValue(message, OrderPlacedEvent.class);

            log.info(
                    "Received order: orderId={}, accountId={}, symbol={}, side={}, quantity={}, createdOn={}",
                    event.orderId(),
                    event.accountId(),
                    event.symbol(),
                    event.side(),
                    event.quantity(),
                    event.createdOn()
            );

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process order message: {}", message, e);
        }
    }
}
package com.tradingsystem.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Optional test-only listener for the `orders` topic.
 * Disabled by default. Enable with app.kafka.test-listener.enabled=true.
 *
 * This listener intentionally uses a separate group id so it cannot interfere
 * with `trade-executor`'s consumer group when enabled.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.test-listener.enabled", havingValue = "true")
public class TestOrdersListener {

    @KafkaListener(
            topics = "${app.kafka.orders-topic:orders}",
            groupId = "${app.kafka.test-listener.group-id:test-listener}"
    )
    public void listen(String message) {
        // A simple debug listener that prints the raw message. Only enable in dev/test.
        System.out.println("KAFKA MESSAGE: " + message);
    }
}
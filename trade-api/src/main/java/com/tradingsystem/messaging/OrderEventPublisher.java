package com.tradingsystem.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

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
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    ordersTopic,
                    event.orderId().toString(),
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
package com.tradingsystem.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.execution.ExecutionService;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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

    private final ExecutionService executionService;

    public OrderConsumer(
            ObjectMapper objectMapper,
            ExecutionService executionService
    ){

        this.objectMapper = objectMapper;

        this.executionService = executionService;
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
            JsonNode root = objectMapper.readTree(message);

            KafkaEvent<OrderPlacedPayload> event;

            if (root.has("payload")) {
                // message is the envelope
                event = objectMapper.readValue(
                        message,
                        new TypeReference<KafkaEvent<OrderPlacedPayload>>() {}
                );
            } else {
                // message is raw payload (older producer). Deserialize payload then wrap.
                OrderPlacedPayload payload =
                        objectMapper.readValue(message, OrderPlacedPayload.class);

                event = new KafkaEvent<>();
                event.setPayload(payload);
                // optionally set event metadata if desired:
                // event.setEventType("ORDER_PLACED");
                // event.setEventId(UUID.randomUUID().toString());
                // event.setEventTime(OffsetDateTime.now(ZoneOffset.UTC).toString());
                // event.setSource("unknown");
                // event.setSchemaVersion(1);
            }

            if (event == null || event.getPayload() == null) {
                log.error("Order execution failed: missing payload - message={}, partition={}, offset={}", message, partition, offset);
                return;
            }

            log.info(
                    "ORDER_PLACED received: orderId={} key={} partition={} offset={}",
                    event.getPayload().getOrderId(), key, partition, offset
            );

            executionService.execute(event.getPayload());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Order execution failed", e);
        }
    }
}
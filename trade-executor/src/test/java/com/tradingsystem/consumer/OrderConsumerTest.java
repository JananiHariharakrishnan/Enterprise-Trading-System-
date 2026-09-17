package com.tradingsystem.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.execution.ExecutionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderConsumerTest {

    private ObjectMapper objectMapper;
    private ExecutionService executionService;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OrderConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        executionService = mock(ExecutionService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        consumer = new OrderConsumer(objectMapper, executionService, kafkaTemplate);
    }

    @Test
    void consumeOrder_validJson_callsExecuteAndAck() throws Exception {
        String message = "{}";
        OrderPlacedPayload payload = new OrderPlacedPayload();
        payload.setOrderId("550e8400-e29b-41d4-a716-446655440000");
        KafkaEvent<OrderPlacedPayload> event = new KafkaEvent<>();
        event.setEventType("ORDER_PLACED");
        event.setPayload(payload);

        // Mock readTree to return a JsonNode with "payload" field (new envelope format)
        JsonNode mockNode = mock(JsonNode.class);
        when(mockNode.has("payload")).thenReturn(true);
        
        when(objectMapper.readTree(anyString())).thenReturn(mockNode);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(event);

        Acknowledgment ack = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0, "key1", message);
        consumer.consumeOrder(record, ack);

        verify(executionService, times(1)).execute(any(OrderPlacedPayload.class));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void consumeOrder_malformedJson_sendsToDltAndAcks() throws Exception {
        String message = "bad";
        when(objectMapper.readTree(anyString())).thenThrow(new JsonProcessingException("boom") {});

        Acknowledgment ack = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0, "key1", message);
        consumer.consumeOrder(record, ack);

        verify(executionService, never()).execute(any());
        verify(kafkaTemplate, times(1)).send("orders.DLT", "key1", message);
        verify(ack, times(1)).acknowledge();
    }
}

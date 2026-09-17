package com.tradingsystem.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.execution.ExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderConsumerTest {

    private ObjectMapper objectMapper;
    private ExecutionService executionService;
    private OrderConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        executionService = mock(ExecutionService.class);
        consumer = new OrderConsumer(objectMapper, executionService);
    }

    @Test
    void consumeOrder_validJson_callsExecuteAndAck() throws Exception {
        String message = "{}";
        OrderPlacedPayload payload = new OrderPlacedPayload();
        payload.setOrderId("o1");
        KafkaEvent<OrderPlacedPayload> event = new KafkaEvent<>();
        event.setPayload(payload);

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(event);

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(message, ack);

        verify(executionService, times(1)).execute(payload);
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void consumeOrder_parseError_logsAndDoesNotAck() throws Exception {
        String message = "bad";
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenThrow(new RuntimeException("boom"));

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(message, ack);

        verify(executionService, never()).execute(any());
        verify(ack, never()).acknowledge();
    }
}

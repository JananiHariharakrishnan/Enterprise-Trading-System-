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
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 0, 0, "key1", message);
        consumer.consumeOrder(consumerRecord, ack);

        verify(executionService, times(1)).execute(any(OrderPlacedPayload.class));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void consumeOrder_malformedJson_sendsToDltAndAcks() throws Exception {
        String message = "bad";
        when(objectMapper.readTree(anyString())).thenThrow(new JsonProcessingException("boom") {});

        Acknowledgment ack = mock(Acknowledgment.class);
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("orders", 0, 0, "key1", message);
        consumer.consumeOrder(consumerRecord, ack);

        verify(executionService, never()).execute(any());
        verify(kafkaTemplate, times(1)).send("orders.DLT", "key1", message);
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void consumeOrder_missingPayload_sendsToDlt() throws Exception {
        JsonNode root = mock(JsonNode.class);
        when(root.has("payload")).thenReturn(true);
        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(new KafkaEvent<>());

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);

        verify(kafkaTemplate).send(eq("orders.DLT"), eq("key1"), eq("{}"));
        verify(ack).acknowledge();
        verify(executionService, never()).execute(any());
    }

    @Test
    void consumeOrder_blankAndInvalidOrderIds_areRejected() throws Exception {
        stubEnvelope(payloadWith("  ", "ORDER_PLACED"));
        Acknowledgment blankAck = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), blankAck);
        verify(blankAck).acknowledge();

        stubEnvelope(payloadWith("not-a-uuid", "ORDER_PLACED"));
        Acknowledgment invalidAck = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), invalidAck);
        verify(invalidAck).acknowledge();
        verify(executionService, never()).execute(any());
    }

    @Test
    void consumeOrder_unexpectedEventType_sendsToDlt() throws Exception {
        KafkaEvent<OrderPlacedPayload> cancelled = validEvent();
        cancelled.setEventType("ORDER_CANCELLED");
        stubEnvelope(cancelled);
        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack).acknowledge();
        verify(executionService, never()).execute(any());
    }

    @Test
    void consumeOrder_legacyPayloadWithoutEnvelope_requiresEventType() throws Exception {
        JsonNode root = mock(JsonNode.class);
        when(root.has("payload")).thenReturn(false);
        when(objectMapper.readTree(anyString())).thenReturn(root);
        OrderPlacedPayload payload = payloadWith("550e8400-e29b-41d4-a716-446655440000", null);
        when(objectMapper.readValue(anyString(), eq(OrderPlacedPayload.class))).thenReturn(payload);

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);

        verify(ack).acknowledge();
        verify(executionService, never()).execute(any());
    }

    @Test
    void consumeOrder_invalidEventFormat_sendsToDlt() throws Exception {
        JsonNode root = mock(JsonNode.class);
        when(root.has("payload")).thenReturn(true);
        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("bad event") {});

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack, atLeastOnce()).acknowledge();
    }

    @Test
    void consumeOrder_copiesEventIdAndAcknowledgesSuccess() throws Exception {
        OrderPlacedPayload payload = payloadWith("550e8400-e29b-41d4-a716-446655440000", "ORDER_PLACED");
        KafkaEvent<OrderPlacedPayload> event = new KafkaEvent<>();
        event.setEventType("ORDER_PLACED");
        event.setEventId("evt-1");
        event.setPayload(payload);
        stubEnvelope(event);

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);

        verify(executionService).execute(payload);
        verify(ack).acknowledge();
        org.junit.jupiter.api.Assertions.assertEquals("evt-1", payload.getEventId());
    }

    @Test
    void consumeOrder_databaseError_isRetriedWithoutAck() throws Exception {
        stubEnvelope(validEvent());
        doThrow(new DataRetrievalFailureException("database down")).when(executionService).execute(any());

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack, never()).acknowledge();
    }

    @Test
    void consumeOrder_permanentFailures_goToDlt() throws Exception {
        assertPermanent("order not found");
        assertPermanent("failed to update position after 3 retries");
        assertPermanent("status is required");
        assertPermanent("");
    }

    @Test
    void consumeOrder_transientFailure_isRethrown() throws Exception {
        stubEnvelope(validEvent());
        doThrow(new IllegalStateException("connection timeout")).when(executionService).execute(any());
        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack, never()).acknowledge();
        verify(kafkaTemplate, never()).send(eq("orders.DLT"), any(), any());
    }

    @Test
    void consumeOrder_unexpectedFailure_goesToDlt() throws Exception {
        stubEnvelope(validEvent());
        doThrow(new IllegalStateException("nope")).when(executionService).execute(any());
        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack).acknowledge();
        verify(kafkaTemplate, atLeastOnce()).send(eq("orders.DLT"), any(), any());
    }

    @Test
    void consumeOrder_dltSendFailure_isSwallowed() throws Exception {
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka down")));
        when(objectMapper.readTree(anyString())).thenThrow(new JsonProcessingException("boom") {});

        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("bad"), ack);
        verify(ack).acknowledge();
    }

    @Test
    void consumeOrder_usesCauseMessageWhenCurrentMessageIsBlank() throws Exception {
        stubEnvelope(validEvent());
        doThrow(new IllegalStateException(" ", new IllegalArgumentException("order not found in database")))
                .when(executionService).execute(any());
        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack).acknowledge();
    }

    @Test
    void consumeOrder_unexpectedExceptionDuringParse_isNotAcked() throws Exception {
        when(objectMapper.readTree(anyString())).thenThrow(new IllegalStateException("mapper exploded"));
        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack, never()).acknowledge();
    }

    private void assertPermanent(String message) throws Exception {
        stubEnvelope(validEvent());
        RuntimeException failure = message.isBlank()
                ? new IllegalStateException((String) null)
                : new IllegalStateException(message);
        doThrow(failure).when(executionService).execute(any());
        Acknowledgment ack = mock(Acknowledgment.class);
        consumer.consumeOrder(record("{}"), ack);
        verify(ack).acknowledge();
    }

    private void stubEnvelope(OrderPlacedPayload payload) throws Exception {
        KafkaEvent<OrderPlacedPayload> event = new KafkaEvent<>();
        event.setEventType("ORDER_PLACED");
        event.setPayload(payload);
        stubEnvelope(event);
    }

    private void stubEnvelope(KafkaEvent<OrderPlacedPayload> event) throws Exception {
        JsonNode root = mock(JsonNode.class);
        when(root.has("payload")).thenReturn(true);
        when(objectMapper.readTree(anyString())).thenReturn(root);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(event);
    }

    private KafkaEvent<OrderPlacedPayload> validEvent() {
        KafkaEvent<OrderPlacedPayload> event = new KafkaEvent<>();
        event.setEventType("ORDER_PLACED");
        event.setPayload(payloadWith("550e8400-e29b-41d4-a716-446655440000", "ORDER_PLACED"));
        return event;
    }

    private OrderPlacedPayload payloadWith(String orderId, String ignored) {
        OrderPlacedPayload payload = new OrderPlacedPayload();
        payload.setOrderId(orderId);
        return payload;
    }

    private ConsumerRecord<String, String> record(String message) {
        return new ConsumerRecord<>("orders", 0, 0, "key1", message);
    }
}

package com.tradingsystem.producer;

import com.tradingsystem.event.KafkaEvent;
import com.tradingsystem.event.payload.TradeEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeEventProducerTest {

    private KafkaTemplate<String, KafkaEvent<TradeEventPayload>> kafkaTemplate;
    private TradeEventProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producer = new TradeEventProducer(kafkaTemplate);
    }

    private TradeEventPayload samplePayload() {
        TradeEventPayload p = new TradeEventPayload();
        p.setOrderId("o1");
        p.setAccountId(42L);
        p.setSymbol("AAPL");
        p.setSide("BUY");
        p.setQuantity(BigDecimal.TEN);
        p.setPrice(new BigDecimal("100.00"));
        p.setExecutedPrice(new BigDecimal("99.50"));
        p.setStatus("FILLED");
        return p;
    }

    @Test
    void publishOrderFilled_sendsKafkaEventWithCorrectMetadata() {
        TradeEventPayload payload = samplePayload();

        producer.publishOrderFilled(payload);

        ArgumentCaptor<KafkaEvent> eventCaptor = ArgumentCaptor.forClass(KafkaEvent.class);
        verify(kafkaTemplate).send(eq("trade-events"), eq(String.valueOf(payload.getAccountId())), eventCaptor.capture());

        KafkaEvent sent = eventCaptor.getValue();
        assertNotNull(sent);
        assertEquals("trade-executor", sent.getSource());
        assertEquals(1, sent.getSchemaVersion());
        assertEquals("ORDER_FILLED", sent.getEventType());
    }

    @Test
    void publishOrderRejected_sendsKafkaEventWithRejectedType() {
        TradeEventPayload payload = samplePayload();
        payload.setStatus("REJECTED");

        producer.publishOrderRejected(payload);

        ArgumentCaptor<KafkaEvent> eventCaptor = ArgumentCaptor.forClass(KafkaEvent.class);
        verify(kafkaTemplate).send(eq("trade-events"), eq(String.valueOf(payload.getAccountId())), eventCaptor.capture());

        KafkaEvent sent = eventCaptor.getValue();
        assertEquals("ORDER_REJECTED", sent.getEventType());
    }
}

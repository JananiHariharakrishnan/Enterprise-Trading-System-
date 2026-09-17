package com.tradingsystem.controller;

import com.tradingsystem.producer.TradeEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestTradeEventControllerTest {

    private TradeEventProducer producer;
    private TestTradeEventController controller;

    @BeforeEach
    void setUp() {
        producer = mock(TradeEventProducer.class);
        controller = new TestTradeEventController(producer);
    }

    @Test
    void publishFilledEvent_callsProducerAndReturnsMessage() {
        String result = controller.publishFilledEvent();
        verify(producer).publishOrderFilled(org.mockito.ArgumentMatchers.any());
        assertEquals("ORDER_FILLED event published", result);
    }
}

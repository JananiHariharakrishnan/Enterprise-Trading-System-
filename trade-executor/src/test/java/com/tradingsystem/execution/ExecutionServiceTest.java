package com.tradingsystem.execution;

import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.event.payload.TradeEventPayload;
import com.tradingsystem.poller.Quote;
import com.tradingsystem.producer.TradeEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionServiceTest {

    private ExecutionRule executionRule;
    private com.tradingsystem.poller.FauxnanceClient fauxnanceClient;
    private TradeEventProducer producer;
    private ExecutionService service;

    @BeforeEach
    void setUp() {
        executionRule = mock(ExecutionRule.class);
        fauxnanceClient = mock(com.tradingsystem.poller.FauxnanceClient.class);
        producer = mock(TradeEventProducer.class);
        service = new ExecutionService(executionRule, fauxnanceClient, producer);
    }

    private OrderPlacedPayload sampleOrder() {
        OrderPlacedPayload o = new OrderPlacedPayload();
        o.setOrderId("o1");
        o.setAccountId(1L);
        o.setSymbol("AAPL");
        o.setSide("BUY");
        o.setQuantity(BigDecimal.TEN);
        o.setPrice(new BigDecimal("100.00"));
        return o;
    }

    @Test
    void execute_nullQuotes_publishesRejected() {
        OrderPlacedPayload order = sampleOrder();
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(null);

        service.execute(order);

        ArgumentCaptor<TradeEventPayload> captor = ArgumentCaptor.forClass(TradeEventPayload.class);
        verify(producer, times(1)).publishOrderRejected(captor.capture());
        TradeEventPayload payload = captor.getValue();
        assertEquals("REJECTED", payload.getStatus());
        assertEquals("Quote unavailable", payload.getReason());
    }

    @Test
    void execute_emptyQuotes_publishesRejected() {
        OrderPlacedPayload order = sampleOrder();
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(List.of());

        service.execute(order);

        verify(producer, times(1)).publishOrderRejected(any(TradeEventPayload.class));
    }

    @Test
    void execute_decisionFilled_publishesFilled() {
        OrderPlacedPayload order = sampleOrder();
        Quote q = new Quote("AAPL", new BigDecimal("100.00"), "USD", null, null, null, null, false, null);
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(List.of(q));
        when(executionRule.decide(anyString(), any(), any())).thenReturn(ExecutionDecision.filled(new BigDecimal("100.00")));

        service.execute(order);

        verify(producer, times(1)).publishOrderFilled(any(TradeEventPayload.class));
    }

    @Test
    void execute_decisionRejected_publishesRejectedWithReason() {
        OrderPlacedPayload order = sampleOrder();
        Quote q = new Quote("AAPL", new BigDecimal("100.00"), "USD", null, null, null, null, false, null);
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(List.of(q));
        when(executionRule.decide(anyString(), any(), any())).thenReturn(ExecutionDecision.rejected("Not marketable"));

        service.execute(order);

        ArgumentCaptor<TradeEventPayload> captor = ArgumentCaptor.forClass(TradeEventPayload.class);
        verify(producer, times(1)).publishOrderRejected(captor.capture());
        assertEquals("Not marketable", captor.getValue().getReason());
    }
}

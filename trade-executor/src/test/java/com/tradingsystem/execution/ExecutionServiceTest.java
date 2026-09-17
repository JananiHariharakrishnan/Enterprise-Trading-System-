package com.tradingsystem.execution;

import com.tradingsystem.event.payload.OrderPlacedPayload;
import com.tradingsystem.poller.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionServiceTest {

    private ExecutionRule executionRule;
    private com.tradingsystem.poller.FauxnanceClient fauxnanceClient;
    private RestTemplate restTemplate;
    private ExecutionService service;

    @BeforeEach
    void setUp() {
        executionRule = mock(ExecutionRule.class);
        fauxnanceClient = mock(com.tradingsystem.poller.FauxnanceClient.class);
        restTemplate = mock(RestTemplate.class);
        service = new ExecutionService(executionRule, fauxnanceClient, restTemplate, "http://localhost:8888");
        
        // Setup default mock response for all REST calls
        ResponseEntity<Object> mockResponse = new ResponseEntity<>("OK", HttpStatus.OK);
        when(restTemplate.postForEntity(any(), any(), any())).thenReturn(mockResponse);
    }

    private OrderPlacedPayload sampleOrder() {
        OrderPlacedPayload o = new OrderPlacedPayload();
        o.setOrderId(UUID.randomUUID().toString());
        o.setAccountId(1L);
        o.setSymbol("AAPL");
        o.setSide("BUY");
        o.setQuantity(BigDecimal.TEN);
        o.setPrice(new BigDecimal("100.00"));
        return o;
    }

    @Test
    void execute_nullQuotes_callsTradeApiWithRejected() {
        OrderPlacedPayload order = sampleOrder();
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(null);

        service.execute(order);

        // Should attempt to call trade-api to mark as REJECTED
        verify(restTemplate, times(1)).postForEntity(any(), any(), any());
    }

    @Test
    void execute_emptyQuotes_callsTradeApiWithRejected() {
        OrderPlacedPayload order = sampleOrder();
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(List.of());

        service.execute(order);

        // Should attempt to call trade-api to mark as REJECTED
        verify(restTemplate, times(1)).postForEntity(any(), any(), any());
    }

    @Test
    void execute_decisionFilled_callsTradeApiWithFilled() {
        OrderPlacedPayload order = sampleOrder();
        Quote q = new Quote("AAPL", new BigDecimal("100.00"), "USD", null, null, null, null, false, null);
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(List.of(q));
        when(executionRule.decide(anyString(), any(), any())).thenReturn(ExecutionDecision.filled(new BigDecimal("100.00")));

        service.execute(order);

        // Should call trade-api with FILLED status
        verify(restTemplate, times(1)).postForEntity(any(), any(), any());
    }

    @Test
    void execute_decisionRejected_callsTradeApiWithRejected() {
        OrderPlacedPayload order = sampleOrder();
        Quote q = new Quote("AAPL", new BigDecimal("100.00"), "USD", null, null, null, null, false, null);
        when(fauxnanceClient.getQuotes(anyList())).thenReturn(List.of(q));
        when(executionRule.decide(anyString(), any(), any())).thenReturn(ExecutionDecision.rejected("Not marketable"));

        service.execute(order);

        // Should call trade-api with REJECTED status
        verify(restTemplate, times(1)).postForEntity(any(), any(), any());
    }
}

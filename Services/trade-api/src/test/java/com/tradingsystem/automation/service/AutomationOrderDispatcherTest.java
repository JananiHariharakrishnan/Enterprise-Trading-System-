package com.tradingsystem.automation.service;

import com.tradingsystem.dto.request.PlaceOrderRequest;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationOrderDispatcherTest {

    @Test
    void placeOrder_marketAndLimit_postsSignedRequest() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AutomationOrderDispatcher dispatcher = new AutomationOrderDispatcher(
                restTemplate, "http://trade.local", "test-secret", "auth-service");
        OrderResponse created = new OrderResponse();
        created.setOrderId("ord-1");

        org.mockito.ArgumentCaptor<HttpEntity<PlaceOrderRequest>> entity =
                org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq("http://trade.local/api/v1/orders"), entity.capture(), eq(OrderResponse.class)))
                .thenReturn(ResponseEntity.ok(created));

        OrderResponse market = dispatcher.placeOrder(4L, "AAPL", OrderSide.BUY, 3, null);
        assertSame(created, market);
        PlaceOrderRequest marketRequest = entity.getValue().getBody();
        assertEquals(OrderType.MARKET, marketRequest.getOrderType());
        assertNull(marketRequest.getPrice());
        assertEquals(4L, marketRequest.getAccountId());
        assertEquals("AAPL", marketRequest.getSymbol());
        assertEquals(OrderSide.BUY, marketRequest.getSide());
        assertEquals(3, marketRequest.getQuantity());
        assertTrue(marketRequest.getIdempotencyKey() != null && !marketRequest.getIdempotencyKey().isBlank());
        assertTrue(entity.getValue().getHeaders().getFirst("Authorization").startsWith("Bearer "));

        when(restTemplate.postForEntity(eq("http://trade.local/api/v1/orders"), entity.capture(), eq(OrderResponse.class)))
                .thenReturn(ResponseEntity.ok(null));
        assertNull(dispatcher.placeOrder(4L, "MSFT", OrderSide.SELL, 1, new BigDecimal("10.50")));
        PlaceOrderRequest limit = entity.getValue().getBody();
        assertEquals(OrderType.LIMIT, limit.getOrderType());
        assertEquals(0, new BigDecimal("10.50").compareTo(limit.getPrice()));
    }

    @Test
    void placeOrder_skipsBlankTokenAndRethrowsHttpAndTransportErrors() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AutomationOrderDispatcher dispatcher = spy(new AutomationOrderDispatcher(
                restTemplate, "http://trade.local", "test-secret", "auth-service"));
        doReturn("  ").when(dispatcher).generateTokenForAccount(8L);

        org.mockito.ArgumentCaptor<HttpEntity<PlaceOrderRequest>> entity =
                org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq("http://trade.local/api/v1/orders"), entity.capture(), eq(OrderResponse.class)))
                .thenReturn(ResponseEntity.ok(new OrderResponse()));

        dispatcher.placeOrder(8L, "IBM", OrderSide.BUY, 1, null);
        assertNull(entity.getValue().getHeaders().getFirst("Authorization"));

        doReturn(null).when(dispatcher).generateTokenForAccount(8L);
        dispatcher.placeOrder(8L, "IBM", OrderSide.BUY, 1, null);
        assertNull(entity.getValue().getHeaders().getFirst("Authorization"));

        HttpClientErrorException rejected = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "conflict", null, "{\"error\":\"dup\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.postForEntity(any(String.class), any(), eq(OrderResponse.class))).thenThrow(rejected);
        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class,
                () -> dispatcher.placeOrder(8L, "IBM", OrderSide.SELL, 2, new BigDecimal("1")));
        assertSame(rejected, thrown);

        when(restTemplate.postForEntity(any(String.class), any(), eq(OrderResponse.class)))
                .thenThrow(new IllegalStateException("connection reset"));
        assertThrows(IllegalStateException.class,
                () -> dispatcher.placeOrder(8L, "IBM", OrderSide.SELL, 2, null));
        verify(restTemplate, org.mockito.Mockito.atLeastOnce())
                .postForEntity(any(String.class), any(), eq(OrderResponse.class));
    }

    @Test
    void generateToken_signsPayloadAndFallsBackWhenSecretMissing() {
        AutomationOrderDispatcher signed = new AutomationOrderDispatcher(
                mock(RestTemplate.class), "http://trade.local", "test-secret", null);
        String token = signed.generateTokenForAccount(42L);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(header.contains("HS256"));
        assertTrue(payload.contains("\"accountId\":42"));
        assertTrue(payload.contains("\"iss\":\"trade-api\""));

        AutomationOrderDispatcher blank = new AutomationOrderDispatcher(
                mock(RestTemplate.class), "http://trade.local", "   ", "auth-service");
        assertEquals("dev-internal-token", blank.generateTokenForAccount(1L));

        AutomationOrderDispatcher missing = new AutomationOrderDispatcher(
                mock(RestTemplate.class), "http://trade.local", null, "auth-service");
        assertEquals("dev-internal-token", missing.generateTokenForAccount(1L));

        AutomationOrderDispatcher empty = new AutomationOrderDispatcher(
                mock(RestTemplate.class), "http://trade.local", "", "iss");
        assertEquals("dev-internal-token", empty.generateTokenForAccount(1L));
    }

    @Test
    void constructors_acceptNullClientAndValueDefaults() {
        AutomationOrderDispatcher noClient = new AutomationOrderDispatcher(null, "http://127.0.0.1:9", "secret", "iss");
        assertTrue(noClient.generateTokenForAccount(3L).split("\\.").length == 3);

        AutomationOrderDispatcher defaults = new AutomationOrderDispatcher();
        assertTrue(defaults.generateTokenForAccount(3L).contains("."));

        AutomationOrderDispatcher configured = new AutomationOrderDispatcher(
                "http://127.0.0.1:9", "another-secret", "auth-service");
        String token = configured.generateTokenForAccount(9L);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"iss\":\"auth-service\""));
        assertTrue(payload.contains("\"accountId\":9"));
    }
}

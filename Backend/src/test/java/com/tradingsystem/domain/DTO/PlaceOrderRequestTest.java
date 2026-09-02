package com.tradingsystem.domain.DTO;

import com.tradingsystem.domain.enums.OrderSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceOrderRequestTest {

    @Test
    void validRequestCanBeCreated() {
        PlaceOrderRequest request = new PlaceOrderRequest();

        request.setAccountId(1L);
        request.setSymbol("ACME");
        request.setSide(OrderSide.BUY);
        request.setQuantity(100);
        request.setPrice(new BigDecimal("25.50"));
        request.setIdempotencyKey("key-12345");

        assertEquals(1L, request.getAccountId());
        assertEquals("ACME", request.getSymbol());
        assertEquals(OrderSide.BUY, request.getSide());
        assertEquals(100, request.getQuantity());
        assertEquals(new BigDecimal("25.50"), request.getPrice());
        assertEquals("key-12345", request.getIdempotencyKey());
    }
}
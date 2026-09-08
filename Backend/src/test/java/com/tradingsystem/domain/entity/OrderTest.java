package com.tradingsystem.domain.entity;

import com.tradingsystem.domain.enums.OrderSide;
import com.tradingsystem.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void orderStoresAccountId() {
        Order order = new Order();

        order.setAccountId(1L);

        assertEquals(1L, order.getAccountId());
    }

    @Test
    void orderStoresSymbol() {
        Order order = new Order();

        order.setSymbol("ACME");

        assertEquals("ACME", order.getSymbol());
    }

    @Test
    void orderStoresSide() {
        Order order = new Order();

        order.setSide(OrderSide.BUY);

        assertEquals(OrderSide.BUY, order.getSide());
    }

    @Test
    void orderStoresQuantity() {
        Order order = new Order();

        order.setQuantity(100);

        assertEquals(100, order.getQuantity());
    }

    @Test
    void orderStoresPriceAsBigDecimal() {
        Order order = new Order();

        order.setPrice(new BigDecimal("25.50"));

        assertEquals(new BigDecimal("25.50"), order.getPrice());
        assertInstanceOf(BigDecimal.class, order.getPrice());
    }

    @Test
    void orderStoresIdempotencyKey() {
        Order order = new Order();

        order.setIdempotencyKey("key-12345");

        assertEquals("key-12345", order.getIdempotencyKey());
    }

    @Test
    void orderStoresStatus() {
        Order order = new Order();

        order.setStatus(OrderStatus.NEW);

        assertEquals(OrderStatus.NEW, order.getStatus());
    }
}
package com.tradingsystem.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPlacedPayloadTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void constructorAndAccessorsRoundTripEveryField() {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                "ord-1",
                42L,
                "AAPL",
                "BUY",
                "LIMIT",
                new BigDecimal("10"),
                new BigDecimal("185.50"),
                "idem-1",
                "2026-10-07T12:00:00Z"
        );

        assertEquals("ord-1", payload.getOrderId());
        assertEquals(42L, payload.getAccountId());
        assertEquals("AAPL", payload.getSymbol());
        assertEquals("BUY", payload.getSide());
        assertEquals("LIMIT", payload.getOrderType());
        assertEquals(new BigDecimal("10"), payload.getQuantity());
        assertEquals(new BigDecimal("185.50"), payload.getPrice());
        assertEquals("idem-1", payload.getIdempotencyKey());
        assertEquals("2026-10-07T12:00:00Z", payload.getCreatedOn());
        assertTrue(validator.validate(payload).isEmpty());
    }

    @Test
    void settersReplaceValuesAndEmptyPayloadHasNoConstraintViolations() {
        OrderPlacedPayload payload = new OrderPlacedPayload();
        assertNull(payload.getOrderId());
        assertNull(payload.getAccountId());
        assertNull(payload.getSymbol());
        assertNull(payload.getSide());
        assertNull(payload.getOrderType());
        assertNull(payload.getQuantity());
        assertNull(payload.getPrice());
        assertNull(payload.getIdempotencyKey());
        assertNull(payload.getCreatedOn());

        payload.setOrderId("ord-2");
        payload.setAccountId(7L);
        payload.setSymbol("INFY.NS");
        payload.setSide("SELL");
        payload.setOrderType("MARKET");
        payload.setQuantity(new BigDecimal("3"));
        payload.setPrice(null);
        payload.setIdempotencyKey("idem-2");
        payload.setCreatedOn("2026-10-07T15:00:00Z");

        assertEquals("ord-2", payload.getOrderId());
        assertEquals(7L, payload.getAccountId());
        assertEquals("INFY.NS", payload.getSymbol());
        assertEquals("SELL", payload.getSide());
        assertEquals("MARKET", payload.getOrderType());
        assertEquals(new BigDecimal("3"), payload.getQuantity());
        assertNull(payload.getPrice());
        assertEquals("idem-2", payload.getIdempotencyKey());
        assertEquals("2026-10-07T15:00:00Z", payload.getCreatedOn());

        Set<ConstraintViolation<OrderPlacedPayload>> violations = validator.validate(payload);
        assertTrue(violations.isEmpty());
    }
}

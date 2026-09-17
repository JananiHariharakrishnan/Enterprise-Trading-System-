package com.tradingsystem.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionTest {

    @Test
    void totalValue_and_validate() {
        Execution e = new Execution();
        e.setOrderId(UUID.randomUUID());
        e.setQuantity(3);
        e.setPrice(new BigDecimal("2.50"));
        e.setExecutedAt(LocalDateTime.now());

        assertEquals(new BigDecimal("7.50"), e.totalValue());
        e.validate();
        assertTrue(e.toString().contains("orderId="));
    }

    @Test
    void validate_throwsOnBadData() {
        Execution e = new Execution();
        e.setOrderId(null);
        e.setQuantity(0);
        e.setPrice(null);
        e.setExecutedAt(null);

        assertThrows(IllegalArgumentException.class, e::validate);
    }
}

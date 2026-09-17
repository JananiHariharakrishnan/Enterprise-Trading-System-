package com.tradingsystem.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidateTest {

    @Test
    void validateOrderValue_missingPrice_throws() {
        Order o = new Order();
        o.setQuantity(1);
        o.setPrice(null);
        assertThrows(IllegalArgumentException.class, o::validateOrderValue);
    }

    @Test
    void validateOrderValue_highPrecision_throws() {
        Order o = new Order();
        o.setQuantity(1);
        // price with scale > 10 when multiplied
        o.setPrice(new BigDecimal("0.00000000001"));
        assertThrows(ArithmeticException.class, o::validateOrderValue);
    }
}

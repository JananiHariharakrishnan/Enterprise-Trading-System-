package com.tradingsystem.execution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionRuleTest {

    private final ExecutionRule rule = new ExecutionRule();

    @Test
    void decide_nullInputs_returnsRejected() {
        ExecutionDecision d = rule.decide(null, null, null);
        assertEquals(ExecutionDecision.Status.REJECTED, d.getStatus());
        assertNotNull(d.getReason());
    }

    @Test
    void decide_buyMarketable_returnsFilled() {
        ExecutionDecision d = rule.decide("BUY", new BigDecimal("101.00"), new BigDecimal("100.00"));
        assertEquals(ExecutionDecision.Status.FILLED, d.getStatus());
        assertEquals(new BigDecimal("100.00"), d.getExecutedPrice());
    }

    @Test
    void decide_buyNotMarketable_returnsRejected() {
        ExecutionDecision d = rule.decide("BUY", new BigDecimal("99.00"), new BigDecimal("100.00"));
        assertEquals(ExecutionDecision.Status.REJECTED, d.getStatus());
    }

    @Test
    void decide_sellMarketable_returnsFilled() {
        ExecutionDecision d = rule.decide("SELL", new BigDecimal("99.00"), new BigDecimal("100.00"));
        assertEquals(ExecutionDecision.Status.FILLED, d.getStatus());
        assertEquals(new BigDecimal("100.00"), d.getExecutedPrice());
    }

    @Test
    void decide_sellNotMarketable_returnsRejected() {
        ExecutionDecision d = rule.decide("SELL", new BigDecimal("101.00"), new BigDecimal("100.00"));
        assertEquals(ExecutionDecision.Status.REJECTED, d.getStatus());
    }
}

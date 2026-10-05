package com.tradingsystem.model;

import com.tradingsystem.enums.BalanceTransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BalanceHistoryTest {

    @Test
    void validate_and_equals_hashcode_toString() {
        BalanceHistory a = new BalanceHistory();
        a.setAccountId(10L);
        a.setType(BalanceTransactionType.DEPOSIT);
        a.setAmount(new BigDecimal("12.34"));
        a.setRelatedOrderId(UUID.randomUUID());

        // validate should not throw
        a.validate();

        BalanceHistory b = new BalanceHistory();
        b.setAccountId(10L);
        b.setType(BalanceTransactionType.DEPOSIT);
        b.setAmount(new BigDecimal("12.34"));
        b.setRelatedOrderId(a.getRelatedOrderId());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.toString().contains("BalanceHistory"));
    }

    @Test
    void validate_throwsForMissingFields() {
        BalanceHistory a = new BalanceHistory();
        a.setAccountId(null);
        a.setType(null);
        a.setAmount(null);

        assertThrows(IllegalArgumentException.class, a::validate);
    }
}

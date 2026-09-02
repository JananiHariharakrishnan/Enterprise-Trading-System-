package com.tradingsystem.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumTest {

    @Test
    void accountStatusContainsExactlyRequiredValues() {
        assertArrayEquals(
                new AccountStatus[] {
                        AccountStatus.ACTIVE,
                        AccountStatus.SUSPENDED,
                        AccountStatus.CLOSED
                },
                AccountStatus.values()
        );
    }

    @Test
    void orderSideContainsExactlyRequiredValues() {
        assertArrayEquals(
                new OrderSide[] {
                        OrderSide.BUY,
                        OrderSide.SELL
                },
                OrderSide.values()
        );
    }

    @Test
    void orderStatusContainsExactlyRequiredValues() {
        assertArrayEquals(
                new OrderStatus[] {
                        OrderStatus.NEW,
                        OrderStatus.FILLED,
                        OrderStatus.REJECTED,
                        OrderStatus.CANCELLED
                },
                OrderStatus.values()
        );
    }
}

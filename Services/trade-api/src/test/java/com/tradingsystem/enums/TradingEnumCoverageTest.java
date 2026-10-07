package com.tradingsystem.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingEnumCoverageTest {

    @Test
    void coversMarketAndNotificationStates() {
        assertEquals(MarketState.UNKNOWN, MarketState.valueOf("UNKNOWN"));
        assertEquals(5, MarketState.values().length);
        assertEquals(NotificationStatus.FAILED, NotificationStatus.valueOf("FAILED"));
        assertEquals(3, NotificationStatus.values().length);
    }
}

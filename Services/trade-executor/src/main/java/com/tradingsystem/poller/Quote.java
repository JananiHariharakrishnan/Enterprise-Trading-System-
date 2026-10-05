package com.tradingsystem.poller;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(
        String symbol,
        BigDecimal price,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        String marketState,
        Boolean stale,
        Instant quoteAsOf
) {
}
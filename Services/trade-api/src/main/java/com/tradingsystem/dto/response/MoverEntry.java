package com.tradingsystem.dto.response;

import java.math.BigDecimal;

/** One gainer or loser. pnl is in the base currency; pnlPercent is against cost (total) or previous close (day). */
public record MoverEntry(
        String symbol,
        BigDecimal pnl,
        BigDecimal pnlPercent,
        boolean stale) {
}

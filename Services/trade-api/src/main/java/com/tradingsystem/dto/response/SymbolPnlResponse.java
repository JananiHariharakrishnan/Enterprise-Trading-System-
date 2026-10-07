package com.tradingsystem.dto.response;

import java.math.BigDecimal;

public record SymbolPnlResponse(
        String symbol,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal totalPnl) {
}

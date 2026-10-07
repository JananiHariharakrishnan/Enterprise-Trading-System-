package com.tradingsystem.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/portfolio/{accountId}/pnl. bySymbol is null unless requested. */
public record PnlResponse(
        Long accountId,
        String baseCurrency,
        String from,
        String to,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal totalPnl,
        List<SymbolPnlResponse> bySymbol,
        String asOf) {
}

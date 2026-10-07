package com.tradingsystem.dto.response;

import java.math.BigDecimal;

/** GET /api/v1/portfolio/{accountId}. Field names are fixed by portfolio-api.yaml. */
public record PortfolioSummaryResponse(
        Long accountId,
        String baseCurrency,
        BigDecimal cashBalance,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealisedPnl,
        BigDecimal unrealisedPnlPercent,
        BigDecimal realisedPnl,
        BigDecimal totalValue,
        int positionCount,
        boolean partial,
        String asOf) {
}

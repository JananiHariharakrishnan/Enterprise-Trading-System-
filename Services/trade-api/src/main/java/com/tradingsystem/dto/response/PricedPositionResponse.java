package com.tradingsystem.dto.response;

import java.math.BigDecimal;

/** One element of GET /api/v1/portfolio/{accountId}/positions. Price fields are null when unpriced. */
public record PricedPositionResponse(
        Long accountId,
        String symbol,
        int quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealisedPnl,
        BigDecimal unrealisedPnlPercent,
        String currency,
        String priceAsOf,
        boolean stale) {
}

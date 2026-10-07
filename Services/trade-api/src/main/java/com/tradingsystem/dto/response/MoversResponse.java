package com.tradingsystem.dto.response;

import java.util.List;

/** EXTENSION (not in portfolio-api.yaml): GET /api/v1/portfolio/{accountId}/movers. */
public record MoversResponse(
        Long accountId,
        String baseCurrency,
        String basis,
        List<MoverEntry> gainers,
        List<MoverEntry> losers,
        boolean partial,
        String asOf) {
}

package com.tradingsystem.poller.dto;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FauxnanceQuoteResponse(
        Data data
) {

    public record Data(
            List<QuoteWrapper> quotes
    ) {}

    public record QuoteWrapper(
            String symbol,
            Boolean stale,
            QuoteData quote
    ) {}

    public record QuoteData(
            String symbol,
            BigDecimal price,
            String currency,
            BigDecimal change,
            BigDecimal changePercent,
            BigDecimal previousClose,
            String marketState,
            Instant asOf
    ) {}
}

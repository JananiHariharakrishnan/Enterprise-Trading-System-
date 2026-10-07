package com.tradingsystem.service;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.model.FillRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure P&L arithmetic. No Spring, no database, no clock, so every rule in
 * portfolio-api.yaml can be unit tested directly.
 *
 * Realised P&L is booked at the moment of a SELL:
 *     (fillPrice - averageCostAtSale) * quantitySold
 * It never looks at today's price. Because the average cost at the time of a
 * sale is not stored anywhere, it is rebuilt by replaying the fills in time
 * order with the same weighted-average rule the executor uses.
 */
public final class PnlCalculator {

    private static final int AVG_SCALE = 10;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PnlCalculator() {
    }

    /** One booked sale. realised is in the instrument's own currency, unrounded. */
    public record SaleBooking(String symbol, String currency, LocalDateTime bookedAt, BigDecimal realised) {
    }

    /**
     * Replays fills (oldest first) per symbol.
     * BUY  : average = (heldQty*avg + qty*price) / (heldQty + qty); nothing is booked.
     * SELL : books (price - avg) * qty; the average is unchanged; at zero it resets.
     * A sell larger than the replayed holding is booked only for the quantity the
     * replay can account for (the rest has no known cost basis).
     */
    public static List<SaleBooking> replaySales(List<FillRecord> fills) {
        Map<String, Integer> held = new HashMap<>();
        Map<String, BigDecimal> average = new HashMap<>();
        List<SaleBooking> sales = new ArrayList<>();

        for (FillRecord fill : fills) {
            String symbol = fill.getSymbol();
            int heldQty = held.getOrDefault(symbol, 0);
            BigDecimal avg = average.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal qty = BigDecimal.valueOf(fill.getQuantity());

            if ("BUY".equalsIgnoreCase(fill.getSide())) {
                int newQty = heldQty + fill.getQuantity();
                BigDecimal totalCost = avg.multiply(BigDecimal.valueOf(heldQty))
                        .add(fill.getPrice().multiply(qty));
                average.put(symbol, totalCost.divide(BigDecimal.valueOf(newQty), AVG_SCALE, RoundingMode.HALF_UP));
                held.put(symbol, newQty);

            } else if ("SELL".equalsIgnoreCase(fill.getSide())) {
                int matched = Math.min(fill.getQuantity(), heldQty);
                if (matched <= 0) {
                    continue;
                }
                BigDecimal realised = fill.getPrice().subtract(avg).multiply(BigDecimal.valueOf(matched));
                sales.add(new SaleBooking(symbol, fill.getCurrency(), fill.getExecutedAt(), realised));

                int remaining = heldQty - matched;
                held.put(symbol, remaining);
                if (remaining == 0) {
                    average.put(symbol, BigDecimal.ZERO);
                }
            }
        }
        return sales;
    }

    /** quantity * averageCost */
    public static BigDecimal costBasis(int quantity, BigDecimal averageCost) {
        return averageCost.multiply(BigDecimal.valueOf(quantity));
    }

    /** quantity * lastPrice */
    public static BigDecimal marketValue(int quantity, BigDecimal lastPrice) {
        return lastPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Percentage points (6.23 means 6.23 per cent). Null when the base is null or zero. */
    public static BigDecimal percent(BigDecimal pnl, BigDecimal base) {
        if (pnl == null || base == null || base.signum() == 0) {
            return null;
        }
        return pnl.multiply(HUNDRED).divide(base, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Two decimal places, half up. Null stays null. */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Converts an amount into the base currency. INR and USD are supported, using one
     * fixed rate (the same constant the executor uses for cash). Any other currency is
     * rejected rather than silently summed as if it were the base.
     */
    public static BigDecimal toBase(BigDecimal amount, String currency, String baseCurrency, BigDecimal inrPerUsd) {
        if (amount == null || currency == null || currency.equalsIgnoreCase(baseCurrency)) {
            return amount;
        }
        if ("INR".equalsIgnoreCase(currency) && "USD".equalsIgnoreCase(baseCurrency)) {
            return amount.divide(inrPerUsd, AVG_SCALE, RoundingMode.HALF_UP);
        }
        if ("USD".equalsIgnoreCase(currency) && "INR".equalsIgnoreCase(baseCurrency)) {
            return amount.multiply(inrPerUsd);
        }
        throw new IllegalArgumentException("Unsupported currency conversion " + currency + " -> " + baseCurrency);
    }

    /** Parses a quote timestamp; null when it is missing or unparseable. */
    public static Instant parseQuoteTime(String quoteAsOf) {
        if (quoteAsOf == null || quoteAsOf.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(quoteAsOf);
        } catch (DateTimeParseException ignored) {
            // fall through to the offset form
        }
        try {
            return OffsetDateTime.parse(quoteAsOf).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * A quote is stale when it is flagged stale, its observation time is unknown, or it is
     * older than maxAge. Stale is a state, not an error: the number is still served.
     */
    public static boolean isStale(LatestMarketPrice quote, Instant now, Duration maxAge) {
        if (quote == null || quote.getPrice() == null) {
            return true;
        }
        if (Boolean.TRUE.equals(quote.getStale())) {
            return true;
        }
        Instant observed = parseQuoteTime(quote.getQuoteAsOf());
        return observed == null || Duration.between(observed, now).compareTo(maxAge) > 0;
    }
}

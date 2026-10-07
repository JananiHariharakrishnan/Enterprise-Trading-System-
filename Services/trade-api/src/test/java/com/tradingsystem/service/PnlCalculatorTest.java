package com.tradingsystem.service;

import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.model.FillRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PnlCalculatorTest {

    private static FillRecord fill(String side, int qty, String price, String when) {
        return new FillRecord("X", side, qty, new BigDecimal(price), LocalDateTime.parse(when), "USD");
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void seedAccountTwo_relianceSale_books6450() {
        // Seed: buy 400 @ 1425, sell 150 @ 1468 -> (1468 - 1425) * 150
        var sales = PnlCalculator.replaySales(List.of(
                fill("BUY", 400, "1425.00", "2026-06-11T09:31:25"),
                fill("SELL", 150, "1468.00", "2026-07-09T10:28:50")));

        assertEquals(1, sales.size());
        assertMoney("6450", sales.get(0).realised());
    }

    @Test
    void buysBookNothing() {
        var sales = PnlCalculator.replaySales(List.of(
                fill("BUY", 100, "10", "2026-01-01T10:00:00"),
                fill("BUY", 100, "20", "2026-01-02T10:00:00")));
        assertTrue(sales.isEmpty());
    }

    @Test
    void weightedAverageIsUsedAndSellLeavesItUnchanged() {
        // avg after two buys = 15. Sell 50 @ 18 -> +150. Sell 150 @ 12 -> -450 (avg still 15).
        var sales = PnlCalculator.replaySales(List.of(
                fill("BUY", 100, "10", "2026-01-01T10:00:00"),
                fill("BUY", 100, "20", "2026-01-02T10:00:00"),
                fill("SELL", 50, "18", "2026-01-03T10:00:00"),
                fill("SELL", 150, "12", "2026-01-04T10:00:00")));

        assertMoney("150", sales.get(0).realised());
        assertMoney("-450", sales.get(1).realised());
    }

    @Test
    void averageResetsWhenPositionIsClosed() {
        var sales = PnlCalculator.replaySales(List.of(
                fill("BUY", 10, "10", "2026-01-01T10:00:00"),
                fill("SELL", 10, "11", "2026-01-02T10:00:00"),
                fill("BUY", 10, "30", "2026-01-03T10:00:00"),
                fill("SELL", 10, "31", "2026-01-04T10:00:00")));

        assertMoney("10", sales.get(0).realised());
        assertMoney("10", sales.get(1).realised()); // measured against 30, not a blend with the old 10
    }

    @Test
    void sellWithNoKnownHoldingBooksNothing() {
        assertTrue(PnlCalculator.replaySales(List.of(fill("SELL", 5, "10", "2026-01-01T10:00:00"))).isEmpty());
    }

    @Test
    void percentMatchesContractExampleAndIsNullOnZeroBase() {
        assertMoney("6.23", PnlCalculator.percent(new BigDecimal("3820"), new BigDecimal("61300")));
        assertNull(PnlCalculator.percent(BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void inrIsConvertedToUsdAndUnknownCurrencyIsRejected() {
        assertMoney("67.1875", PnlCalculator.toBase(new BigDecimal("6450"), "INR", "USD", new BigDecimal("96")));
        assertMoney("5", PnlCalculator.toBase(new BigDecimal("5"), "USD", "USD", new BigDecimal("96")));
        assertThrows(IllegalArgumentException.class,
                () -> PnlCalculator.toBase(BigDecimal.ONE, "EUR", "USD", new BigDecimal("96")));
    }

    @Test
    void staleRules() {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        Duration max = Duration.ofMinutes(5);
        LatestMarketPrice q = new LatestMarketPrice("A", BigDecimal.TEN, "USD", null, null, null,
                "REGULAR", false, "2026-10-07T11:58:00Z");

        assertFalse(PnlCalculator.isStale(q, now, max));

        q.setQuoteAsOf("2026-10-07T11:00:00Z");           // too old
        assertTrue(PnlCalculator.isStale(q, now, max));

        q.setQuoteAsOf("2026-10-07T11:59:00Z");
        q.setStale(true);                                  // flagged by the feed
        assertTrue(PnlCalculator.isStale(q, now, max));

        q.setStale(false);
        q.setQuoteAsOf(null);                              // unknown observation time
        assertTrue(PnlCalculator.isStale(q, now, max));

        assertTrue(PnlCalculator.isStale(null, now, max)); // no quote at all
    }
}

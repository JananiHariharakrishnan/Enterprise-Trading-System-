package com.tradingsystem.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class WatchlistEntry {

    private Long watchlistId;
    private String symbol;
    private OffsetDateTime addedOn;

    public Long getWatchlistId() {
        return watchlistId;
    }

    public void setWatchlistId(Long watchlistId) {
        this.watchlistId = watchlistId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OffsetDateTime getAddedOn() {
        return addedOn;
    }

    public void setAddedOn(OffsetDateTime addedOn) {
        this.addedOn = addedOn;
    }

    public boolean isRecentlyAddedHours(int hours) {
        if (this.addedOn == null) return false;
        return this.addedOn.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusHours(hours));
    }

    public void validate() {
        if (this.watchlistId == null || this.watchlistId <= 0) throw new IllegalArgumentException("watchlistId required");
        if (this.symbol == null || this.symbol.trim().isEmpty()) throw new IllegalArgumentException("symbol required");
    }

    @Override
    public String toString() {
        return "WatchlistEntry{" +
                "watchlistId=" + watchlistId +
                ", symbol='" + symbol + '\'' +
                ", addedOn=" + addedOn +
                '}';
    }
}

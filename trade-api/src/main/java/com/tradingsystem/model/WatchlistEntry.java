package com.tradingsystem.model;

import java.time.LocalDateTime;

public class WatchlistEntry {

    private Long accountId;
    private String symbol;
    private LocalDateTime addedOn;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDateTime getAddedOn() {
        return addedOn;
    }

    public void setAddedOn(LocalDateTime addedOn) {
        this.addedOn = addedOn;
    }

    public boolean isRecentlyAddedHours(int hours) {
        if (this.addedOn == null) return false;
        return this.addedOn.isAfter(LocalDateTime.now().minusHours(hours));
    }

    public void validate() {
        if (this.accountId == null || this.accountId <= 0) throw new IllegalArgumentException("accountId required");
        if (this.symbol == null || this.symbol.trim().isEmpty()) throw new IllegalArgumentException("symbol required");
    }

    @Override
    public String toString() {
        return "WatchlistEntry{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", addedOn=" + addedOn +
                '}';
    }
}

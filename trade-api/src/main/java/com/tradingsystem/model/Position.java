package com.tradingsystem.model;

import java.math.BigDecimal;

public class Position {

    private Long accountId;
    private String symbol;
    private int quantity;
    private BigDecimal averageCost;
    private int version; // Optimistic locking counter

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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void validateQuantity() {
        // EDGE CASE: Ensure quantity doesn't go negative
        if (this.quantity < 0) {
            throw new IllegalArgumentException("Position quantity cannot be negative");
        }
    }
}

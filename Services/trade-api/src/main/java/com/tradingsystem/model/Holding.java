package com.tradingsystem.model;

import java.math.BigDecimal;

/** Read-only projection: one open position plus its instrument's own currency. */
public class Holding {

    private String symbol;
    private int quantity;
    private BigDecimal averageCost;
    private String currency;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getAverageCost() { return averageCost; }
    public void setAverageCost(BigDecimal averageCost) { this.averageCost = averageCost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

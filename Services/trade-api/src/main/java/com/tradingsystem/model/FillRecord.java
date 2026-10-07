package com.tradingsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only projection: one execution of a FILLED order, with side, symbol and currency. */
public class FillRecord {

    private String symbol;
    private String side;
    private int quantity;
    private BigDecimal price;
    private LocalDateTime executedAt;
    private String currency;

    public FillRecord() {
    }

    public FillRecord(String symbol, String side, int quantity, BigDecimal price,
                      LocalDateTime executedAt, String currency) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.executedAt = executedAt;
        this.currency = currency;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

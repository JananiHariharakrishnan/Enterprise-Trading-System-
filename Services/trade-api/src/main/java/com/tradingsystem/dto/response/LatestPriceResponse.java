package com.tradingsystem.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class LatestPriceResponse {

    private BigDecimal price;
    private String currency;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal previousClose;
    private String marketState;
    private boolean stale;
    private OffsetDateTime quoteAsOf;

    public LatestPriceResponse() {
        // Empty Constructor
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getChange() {
        return change;
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public String getMarketState() {
        return marketState;
    }

    public void setMarketState(String marketState) {
        this.marketState = marketState;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public OffsetDateTime getQuoteAsOf() {
        return quoteAsOf;
    }

    public void setQuoteAsOf(OffsetDateTime quoteAsOf) {
        this.quoteAsOf = quoteAsOf;
    }
}


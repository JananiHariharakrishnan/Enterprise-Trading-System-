package com.tradingsystem.dto.response;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * In-memory representation of the latest market quote for an instrument.
 *
 * Cached from market-data Kafka events and used by REST APIs and SSE
 * to provide the latest market state to clients.
 */
public class LatestMarketPrice {

    private String symbol;
    private BigDecimal price;
    private String currency;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal previousClose;
    private String marketState;
    private Boolean stale;
    private String quoteAsOf;

    public LatestMarketPrice() {
    }

    public LatestMarketPrice(
            String symbol,
            BigDecimal price,
            String currency,
            BigDecimal change,
            BigDecimal changePercent,
            BigDecimal previousClose,
            String marketState,
            Boolean stale,
            String quoteAsOf
    ) {
        this.symbol = symbol;
        this.price = price;
        this.currency = currency;
        this.change = change;
        this.changePercent = changePercent;
        this.previousClose = previousClose;
        this.marketState = marketState;
        this.stale = stale;
        this.quoteAsOf = quoteAsOf;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public Boolean getStale() {
        return stale;
    }

    public void setStale(Boolean stale) {
        this.stale = stale;
    }

    public String getQuoteAsOf() {
        return quoteAsOf;
    }

    public void setQuoteAsOf(String quoteAsOf) {
        this.quoteAsOf = quoteAsOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LatestMarketPrice that = (LatestMarketPrice) o;

        return Objects.equals(symbol, that.symbol)
                && Objects.equals(price, that.price)
                && Objects.equals(currency, that.currency)
                && Objects.equals(change, that.change)
                && Objects.equals(changePercent, that.changePercent)
                && Objects.equals(previousClose, that.previousClose)
                && Objects.equals(marketState, that.marketState)
                && Objects.equals(stale, that.stale)
                && Objects.equals(quoteAsOf, that.quoteAsOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                symbol,
                price,
                currency,
                change,
                changePercent,
                previousClose,
                marketState,
                stale,
                quoteAsOf
        );
    }

    @Override
    public String toString() {
        return "LatestMarketPrice{" +
                "symbol='" + symbol + '\'' +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                ", change=" + change +
                ", changePercent=" + changePercent +
                ", previousClose=" + previousClose +
                ", marketState='" + marketState + '\'' +
                ", stale=" + stale +
                ", quoteAsOf='" + quoteAsOf + '\'' +
                '}';
    }
}
package com.tradingsystem.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Market-data quote payload as specified in contracts/kafka-topics.md
 * Deserialized from the market-data topic events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotePayload {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("change")
    private BigDecimal change;

    @JsonProperty("changePercent")
    private BigDecimal changePercent;

    @JsonProperty("previousClose")
    private BigDecimal previousClose;

    @JsonProperty("marketState")
    private String marketState;

    @JsonProperty("stale")
    private Boolean stale;

    @JsonProperty("quoteAsOf")
    private String quoteAsOf;

    public QuotePayload() {}

    public QuotePayload(String symbol, BigDecimal price, String currency, BigDecimal change,
                        BigDecimal changePercent, BigDecimal previousClose, String marketState,
                        Boolean stale, String quoteAsOf) {
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
    public String toString() {
        return "QuotePayload{" +
                "symbol='" + symbol + '\'' +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                ", marketState='" + marketState + '\'' +
                ", quoteAsOf='" + quoteAsOf + '\'' +
                '}';
    }
}


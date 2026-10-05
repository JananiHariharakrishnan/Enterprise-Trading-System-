package com.tradingsystem.dto.response;

import java.time.OffsetDateTime;

public class WatchlistInstrumentResponse {

    private String symbol;
    private OffsetDateTime addedOn;
    private LatestPriceResponse latestPrice;

    public WatchlistInstrumentResponse() {
        // Empty Constructor
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

    public LatestPriceResponse getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(LatestPriceResponse latestPrice) {
        this.latestPrice = latestPrice;
    }
}


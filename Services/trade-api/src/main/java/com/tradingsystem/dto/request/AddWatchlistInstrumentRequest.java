package com.tradingsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddWatchlistInstrumentRequest {

    @NotBlank
    @Size(min = 1, max = 20)
    private String symbol;

    public AddWatchlistInstrumentRequest() {
        // Empty Constructor
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}


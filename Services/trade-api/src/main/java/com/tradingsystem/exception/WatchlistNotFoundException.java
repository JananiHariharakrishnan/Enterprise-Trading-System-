package com.tradingsystem.exception;

public class WatchlistNotFoundException extends TradingDomainException {

    public WatchlistNotFoundException() {
        super("WAT-404", "Watchlist not found");
    }

    public WatchlistNotFoundException(String message) {
        super("WAT-404", message);
    }
}


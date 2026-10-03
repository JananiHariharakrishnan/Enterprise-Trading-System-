package com.tradingsystem.exception;

public class WatchlistConflictException extends TradingDomainException {

    public WatchlistConflictException() {
        super("WAT-409", "Watchlist already exists");
    }

    public WatchlistConflictException(String message) {
        super("WAT-409", message);
    }
}


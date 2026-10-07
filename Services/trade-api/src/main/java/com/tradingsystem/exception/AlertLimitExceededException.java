package com.tradingsystem.exception;

public class AlertLimitExceededException extends TradingDomainException {

    public AlertLimitExceededException(int limit) {
        super("ALT-400", "Maximum active alerts limit (" + limit + ") exceeded for this account");
    }

    public AlertLimitExceededException(String message) {
        super("ALT-400", message);
    }
}

package com.tradingsystem.exception;

public class InvalidOrderException extends TradingDomainException {
    public InvalidOrderException() {
        super(
                "VAL-422",
                "Invalid order"
        );
    }
}

package com.tradingsystem.exception;

public class InvalidOrderException extends TradingDomainException {
    public InvalidOrderException() {
        super(
                "VAL-422",
                "Invalid order"
        );
    }

    public InvalidOrderException(String message) {
        super(
                "VAL-422",
                message
        );
    }
}

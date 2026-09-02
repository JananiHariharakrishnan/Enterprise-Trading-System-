package com.tradingsystem.domain.exceptions;

public class InvalidOrderException extends TradingDomainException {
    public InvalidOrderException() {
        super(
                "VAL-422",
                "Invalid order"
        );
    }
}

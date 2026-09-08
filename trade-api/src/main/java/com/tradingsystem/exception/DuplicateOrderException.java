package com.tradingsystem.exception;

public class DuplicateOrderException extends TradingDomainException {

    public DuplicateOrderException() {
        super(
                "ORD-409",
                "Duplicate order"
        );
    }
}
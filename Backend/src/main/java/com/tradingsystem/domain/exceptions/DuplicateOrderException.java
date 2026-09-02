package com.tradingsystem.domain.exceptions;

public class DuplicateOrderException extends TradingDomainException {

    public DuplicateOrderException() {
        super(
                "ORD-409",
                "Duplicate order"
        );
    }
}
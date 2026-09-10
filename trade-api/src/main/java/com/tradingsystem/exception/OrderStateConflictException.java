package com.tradingsystem.exception;

public class OrderStateConflictException extends TradingDomainException {

    public OrderStateConflictException() {
        super(
                "ORD-409",
                "Order state conflict"
        );
    }

    public OrderStateConflictException(String message) {
        super(
                "ORD-409",
                message
        );
    }
}


package com.tradingsystem.exception;

public class OrderNotFoundException extends TradingDomainException {

    public OrderNotFoundException() {
        super("ORD-404", "Order not found");
    }

    public OrderNotFoundException(String message) {
        super("ORD-404", message);
    }
}


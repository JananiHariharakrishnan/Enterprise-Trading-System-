package com.tradingsystem.exception;

public class InsufficientHoldingsException extends TradingDomainException {

    public InsufficientHoldingsException() {
        super(
                "ORD-409",
                "Insufficient holdings"
        );
    }
}
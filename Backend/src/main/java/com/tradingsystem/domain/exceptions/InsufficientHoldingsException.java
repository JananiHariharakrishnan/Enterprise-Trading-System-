package com.tradingsystem.domain.exceptions;

public class InsufficientHoldingsException extends TradingDomainException {

    public InsufficientHoldingsException() {
        super(
                "ORD-409",
                "Insufficient holdings"
        );
    }
}
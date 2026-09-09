package com.tradingsystem.exception;

public class InsufficientFundsException extends TradingDomainException {

    public InsufficientFundsException() {
        super(
                "ORD-400",
                "Insufficient funds"
        );
    }
}

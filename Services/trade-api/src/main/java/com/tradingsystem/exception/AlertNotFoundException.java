package com.tradingsystem.exception;

public class AlertNotFoundException extends TradingDomainException {

    public AlertNotFoundException() {
        super("ALT-404", "Alert not found");
    }

    public AlertNotFoundException(String message) {
        super("ALT-404", message);
    }
}

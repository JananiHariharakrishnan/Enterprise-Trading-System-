package com.tradingsystem.exception;

public class AlertStateException extends TradingDomainException {

    public AlertStateException() {
        super("ALT-409", "Invalid alert state or transition");
    }

    public AlertStateException(String message) {
        super("ALT-409", message);
    }
}

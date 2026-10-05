package com.tradingsystem.exception;

public abstract class TradingDomainException extends RuntimeException {

    private final String code;

    protected TradingDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
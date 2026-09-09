package com.tradingsystem.exception;

public class AccountNotActiveException extends TradingDomainException {

    public AccountNotActiveException() {
        super("ACC-403", "Account is not active");
    }
}
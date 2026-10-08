package com.tradingsystem.exception;

/**
 * Thrown when password confirmation fails during a wallet deposit.
 * Mapped to HTTP 401 Unauthorized.
 */
public class InvalidWalletPasswordException extends TradingDomainException {
    public InvalidWalletPasswordException() {
        super("AUTH-401", "Password confirmation failed: Invalid password");
    }
}

package com.tradingsystem.exception;

public class AccountNotFoundException extends TradingDomainException {

  public AccountNotFoundException() {
    super(
            "ACC-404",
            "Account not found"
    );
  }
}
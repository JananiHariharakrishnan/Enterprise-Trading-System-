package com.tradingsystem.domain.exceptions;

public class AccountNotFoundException extends TradingDomainException {

  public AccountNotFoundException() {
    super(
            "ACC-404",
            "Account not found"
    );
  }
}
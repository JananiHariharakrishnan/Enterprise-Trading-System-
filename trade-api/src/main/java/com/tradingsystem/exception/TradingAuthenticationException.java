package com.tradingsystem.exception;

public class TradingAuthenticationException
        extends TradingDomainException {

  public TradingAuthenticationException() {
    super(
            "AUTH-401",
            "Unauthorised"
    );
  }
}
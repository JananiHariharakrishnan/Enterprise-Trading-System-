package com.tradingsystem.exception;

public class InstrumentNotFoundException extends TradingDomainException {

  public InstrumentNotFoundException() {
    super(
            "INS-404",
            "Instrument not found"
    );
  }
}

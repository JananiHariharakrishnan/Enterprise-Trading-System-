package com.tradingsystem.domain.exceptions;

public class InstrumentNotFoundException extends TradingDomainException {

  public InstrumentNotFoundException() {
    super(
            "INS-404",
            "Instrument not found"
    );
  }
}

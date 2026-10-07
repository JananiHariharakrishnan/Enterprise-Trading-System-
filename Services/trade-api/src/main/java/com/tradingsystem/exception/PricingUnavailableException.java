package com.tradingsystem.exception;

/** No held instrument could be priced. Mapped to 503 / MKT-503. */
public class PricingUnavailableException extends TradingDomainException {

    public PricingUnavailableException() {
        super("MKT-503", "Pricing unavailable");
    }
}

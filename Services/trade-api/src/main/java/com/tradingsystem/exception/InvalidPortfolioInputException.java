package com.tradingsystem.exception;

/** Portfolio request failed validation (for example from later than to). Mapped to 422 / VAL-422. */
public class InvalidPortfolioInputException extends TradingDomainException {

    public InvalidPortfolioInputException() {
        super("VAL-422", "Invalid input");
    }
}

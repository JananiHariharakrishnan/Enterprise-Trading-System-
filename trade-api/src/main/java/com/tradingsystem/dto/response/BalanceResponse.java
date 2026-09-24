
package com.tradingsystem.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class BalanceResponse {

    private Long accountId;
    private BigDecimal cashBalance;
    private String currency;
    private OffsetDateTime asOf;

    //default constructor
    public BalanceResponse() {
        // Empty Constructor
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public OffsetDateTime getAsOf() {
        return asOf;
    }

    public void setAsOf(OffsetDateTime asOf) {
        this.asOf = asOf;
    }
}



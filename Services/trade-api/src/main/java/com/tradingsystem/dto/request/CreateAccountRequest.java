package com.tradingsystem.dto.request;

import java.math.BigDecimal;

public class CreateAccountRequest {
    private String accountReference;
    private BigDecimal initialBalance;
    private Integer count;

    public CreateAccountRequest() {
    }

    public CreateAccountRequest(String accountReference, BigDecimal initialBalance) {
        this.accountReference = accountReference;
        this.initialBalance = initialBalance;
    }

    public CreateAccountRequest(Integer count, BigDecimal initialBalance) {
        this.count = count;
        this.initialBalance = initialBalance;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public void setAccountReference(String accountReference) {
        this.accountReference = accountReference;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}

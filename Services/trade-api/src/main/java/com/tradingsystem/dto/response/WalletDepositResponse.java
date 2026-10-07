package com.tradingsystem.dto.response;

import java.math.BigDecimal;

public class WalletDepositResponse {
    private Long accountId;
    private String accountReference;
    private BigDecimal previousBalance;
    private BigDecimal depositedAmount;
    private BigDecimal newCashBalance;
    private BigDecimal newBuyingPower;
    private String message;

    public WalletDepositResponse() {
    }

    public WalletDepositResponse(Long accountId, String accountReference, BigDecimal previousBalance,
                                 BigDecimal depositedAmount, BigDecimal newCashBalance,
                                 BigDecimal newBuyingPower, String message) {
        this.accountId = accountId;
        this.accountReference = accountReference;
        this.previousBalance = previousBalance;
        this.depositedAmount = depositedAmount;
        this.newCashBalance = newCashBalance;
        this.newBuyingPower = newBuyingPower;
        this.message = message;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public void setAccountReference(String accountReference) {
        this.accountReference = accountReference;
    }

    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    public void setPreviousBalance(BigDecimal previousBalance) {
        this.previousBalance = previousBalance;
    }

    public BigDecimal getDepositedAmount() {
        return depositedAmount;
    }

    public void setDepositedAmount(BigDecimal depositedAmount) {
        this.depositedAmount = depositedAmount;
    }

    public BigDecimal getNewCashBalance() {
        return newCashBalance;
    }

    public void setNewCashBalance(BigDecimal newCashBalance) {
        this.newCashBalance = newCashBalance;
    }

    public BigDecimal getNewBuyingPower() {
        return newBuyingPower;
    }

    public void setNewBuyingPower(BigDecimal newBuyingPower) {
        this.newBuyingPower = newBuyingPower;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

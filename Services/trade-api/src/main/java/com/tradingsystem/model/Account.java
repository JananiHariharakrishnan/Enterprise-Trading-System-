package com.tradingsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tradingsystem.enums.AccountStatus;

public class Account {
    private Long accountId;
    private String accountReference;
    private BigDecimal cashBalance;
    private BigDecimal buyingPower;
    private AccountStatus status;
    private int version;


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
    public BigDecimal getCashBalance() {
        return cashBalance;
    }
    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }
    public BigDecimal getBuyingPower() {
        return buyingPower;
    }
    public void setBuyingPower(BigDecimal buyingPower) {
        this.buyingPower = buyingPower;
    }
    public AccountStatus getStatus() {
        return status;
    }
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    public int getVersion() {
        return version;
    }
    public void setVersion(int version) {
        this.version = version;
    }
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    public User getHolder() {
        return holder;
    }
    public void setHolder(User holder) {
        this.holder = holder;
    }
    private LocalDateTime lastUpdated;
    private User holder;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean canAfford(BigDecimal amount) {
        // EDGE CASE FIX: Handle null cash balance
        if (this.cashBalance == null || amount == null) {
            return false;
        }
        return this.cashBalance.compareTo(amount) >= 0;
    }

    public void credit(BigDecimal amount) {
        // EDGE CASE FIX: Null check and negative amount check
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid credit amount");
        }
        if (this.cashBalance == null) {
            this.cashBalance = BigDecimal.ZERO;
        }
        this.cashBalance = this.cashBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        // EDGE CASE FIX: Null check before canAfford
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid debit amount");
        }
        if (!canAfford(amount)) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        this.cashBalance = this.cashBalance.subtract(amount);
    }
}

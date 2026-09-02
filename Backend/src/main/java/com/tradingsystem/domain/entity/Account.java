package com.tradingsystem.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tradingsystem.domain.enums.AccountStatus;

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
        throw new UnsupportedOperationException("TODO: implement isActive");
    }

    public void credit(BigDecimal amount) {
        throw new UnsupportedOperationException("TODO: implement credit");
    }

    public void debit(BigDecimal amount) {
        throw new UnsupportedOperationException("TODO: implement debit");
    }

    public boolean canAfford(BigDecimal amount) {
        throw new UnsupportedOperationException("TODO: implement canAfford");
    }
}

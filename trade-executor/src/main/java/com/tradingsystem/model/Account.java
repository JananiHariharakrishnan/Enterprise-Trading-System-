package com.tradingsystem.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account entity representing a customer trading account.
 */
@Entity
@Table(name = "customer_accounts")
public class Account {
    
    @Id
    @Column(name = "account_id")
    private Long accountId;
    
    @Column(name = "account_reference", length = 50)
    private String accountReference;
    
    @Column(name = "cash_balance", precision = 19, scale = 2)
    private BigDecimal cashBalance;
    
    @Column(name = "buying_power", precision = 19, scale = 2)
    private BigDecimal buyingPower;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "version")
    @Version
    private Integer version;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public Account() {}

    // Getters and Setters
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getAccountReference() { return accountReference; }
    public void setAccountReference(String accountReference) { this.accountReference = accountReference; }

    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }

    public BigDecimal getBuyingPower() { return buyingPower; }
    public void setBuyingPower(BigDecimal buyingPower) { this.buyingPower = buyingPower; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    /**
     * Check if this account can afford a given amount.
     */
    public boolean canAfford(BigDecimal amount) {
        if (this.cashBalance == null || amount == null) {
            return false;
        }
        return this.cashBalance.compareTo(amount) >= 0;
    }

    /**
     * Check if the account is active.
     */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }
}



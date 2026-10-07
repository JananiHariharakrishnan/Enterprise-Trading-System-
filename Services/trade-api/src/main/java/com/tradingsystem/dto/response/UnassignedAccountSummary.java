package com.tradingsystem.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UnassignedAccountSummary {

    private Long accountId;
    private String accountReference;
    private BigDecimal cashBalance;
    private BigDecimal buyingPower;
    private String status;
    private boolean unassigned;
    private String assignedUsername;
    private LocalDateTime lastUpdated;

    public UnassignedAccountSummary() {
    }

    public UnassignedAccountSummary(Long accountId, String accountReference, BigDecimal cashBalance,
                                    BigDecimal buyingPower, String status, boolean unassigned,
                                    String assignedUsername, LocalDateTime lastUpdated) {
        this.accountId = accountId;
        this.accountReference = accountReference;
        this.cashBalance = cashBalance;
        this.buyingPower = buyingPower;
        this.status = status;
        this.unassigned = unassigned;
        this.assignedUsername = assignedUsername;
        this.lastUpdated = lastUpdated;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isUnassigned() {
        return unassigned;
    }

    public void setUnassigned(boolean unassigned) {
        this.unassigned = unassigned;
    }

    public String getAssignedUsername() {
        return assignedUsername;
    }

    public void setAssignedUsername(String assignedUsername) {
        this.assignedUsername = assignedUsername;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

package com.tradingsystem.dto.response;

import java.math.BigDecimal;

public class AdminDashboardStats {
    private BigDecimal totalMoney = BigDecimal.ZERO;
    private long totalAccounts = 0;
    private long openAccounts = 0;
    private long suspendedAccounts = 0;
    private long closedAccounts = 0;
    private long totalUsers = 0;
    private long unmappedAccounts = 0;

    public AdminDashboardStats() {
    }

    public AdminDashboardStats(BigDecimal totalMoney, long totalAccounts, long openAccounts,
                               long suspendedAccounts, long closedAccounts, long totalUsers,
                               long unmappedAccounts) {
        this.totalMoney = totalMoney != null ? totalMoney : BigDecimal.ZERO;
        this.totalAccounts = totalAccounts;
        this.openAccounts = openAccounts;
        this.suspendedAccounts = suspendedAccounts;
        this.closedAccounts = closedAccounts;
        this.totalUsers = totalUsers;
        this.unmappedAccounts = unmappedAccounts;
    }

    public BigDecimal getTotalMoney() {
        return totalMoney;
    }

    public void setTotalMoney(BigDecimal totalMoney) {
        this.totalMoney = totalMoney != null ? totalMoney : BigDecimal.ZERO;
    }

    public long getTotalAccounts() {
        return totalAccounts;
    }

    public void setTotalAccounts(long totalAccounts) {
        this.totalAccounts = totalAccounts;
    }

    public long getOpenAccounts() {
        return openAccounts;
    }

    public void setOpenAccounts(long openAccounts) {
        this.openAccounts = openAccounts;
    }

    public long getSuspendedAccounts() {
        return suspendedAccounts;
    }

    public void setSuspendedAccounts(long suspendedAccounts) {
        this.suspendedAccounts = suspendedAccounts;
    }

    public long getClosedAccounts() {
        return closedAccounts;
    }

    public void setClosedAccounts(long closedAccounts) {
        this.closedAccounts = closedAccounts;
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getUnmappedAccounts() {
        return unmappedAccounts;
    }

    public void setUnmappedAccounts(long unmappedAccounts) {
        this.unmappedAccounts = unmappedAccounts;
    }
}

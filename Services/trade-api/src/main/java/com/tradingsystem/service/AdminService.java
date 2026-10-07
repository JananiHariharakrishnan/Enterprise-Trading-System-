package com.tradingsystem.service;

import com.tradingsystem.dto.request.CreateAccountRequest;
import com.tradingsystem.dto.response.AdminDashboardStats;
import com.tradingsystem.dto.response.AdminUserSummary;
import com.tradingsystem.dto.response.UnassignedAccountSummary;
import com.tradingsystem.model.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AdminService {

    /**
     * Retrieve aggregated platform metrics for the administrator dashboard.
     */
    AdminDashboardStats getDashboardStats();

    /**
     * Retrieve all registered users with their account references, balances, and statuses.
     */
    List<AdminUserSummary> getAllUsers();

    /**
     * Retrieve all unassigned pool accounts and dormant (suspended or closed) accounts.
     */
    List<UnassignedAccountSummary> getUnassignedAndDormantAccounts();

    /**
     * Suspend, close, or reactivate a user's trading account by user UUID.
     */
    void updateUserAccountStatus(UUID userId, String status);

    /**
     * Suspend, close, or reactivate a trading account by account ID.
     */
    void updateAccountStatus(Long accountId, String status);

    /**
     * Create a new unmapped account that is not associated with any user, replenishing the pool.
     */
    Account createUnmappedAccount(CreateAccountRequest request);

    /**
     * Create multiple unmapped accounts in bulk with ACTIVE status and initial balance.
     */
    List<Account> createBatchAccounts(int count, BigDecimal initialBalance);
}

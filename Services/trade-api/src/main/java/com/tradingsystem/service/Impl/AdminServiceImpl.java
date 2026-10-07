package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.request.CreateAccountRequest;
import com.tradingsystem.dto.response.AdminDashboardStats;
import com.tradingsystem.dto.response.AdminUserSummary;
import com.tradingsystem.dto.response.UnassignedAccountSummary;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.AdminUserRecord;
import com.tradingsystem.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "SUSPENDED", "CLOSED");
    private static final String ACCOUNT_PREFIX = "ETP-220";
    private static final long DEFAULT_SEQUENCE = 1; // Will start from ETP-2201
    private static volatile AtomicLong accountSequence = new AtomicLong(DEFAULT_SEQUENCE);
    private static volatile boolean sequenceInitialized = false;

    private final AccountDataMapper accountDataMapper;
    private final UserDataMapper userDataMapper;

    public AdminServiceImpl(AccountDataMapper accountDataMapper, UserDataMapper userDataMapper) {
        this.accountDataMapper = accountDataMapper;
        this.userDataMapper = userDataMapper;
        // Initialize sequence from database on service creation
        initializeSequenceFromDatabase();
    }

    /**
     * Initialize the account sequence by querying the database for the max account reference.
     * Extracts the numeric part and sets the sequence to the next number.
     */
    private synchronized void initializeSequenceFromDatabase() {
        if (sequenceInitialized) {
            return;
        }
        
        try {
            String maxReference = accountDataMapper.findMaxAccountReference();
            long nextSequence = DEFAULT_SEQUENCE;
            
            if (maxReference != null && maxReference.startsWith(ACCOUNT_PREFIX)) {
                // Extract numeric part: "ETP-22020" -> "20"
                String numericPart = maxReference.substring(ACCOUNT_PREFIX.length());
                try {
                    long maxNumber = Long.parseLong(numericPart);
                    nextSequence = maxNumber + 1;
                    log.info("Initialized account sequence to {} from max reference: {}", nextSequence, maxReference);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse numeric part from account reference: {}, using default sequence", maxReference);
                }
            } else {
                log.info("No existing accounts found, starting sequence from {}", nextSequence);
            }
            
            accountSequence = new AtomicLong(nextSequence);
            sequenceInitialized = true;
        } catch (Exception e) {
            log.error("Error initializing account sequence from database, using default", e);
            accountSequence = new AtomicLong(DEFAULT_SEQUENCE);
            sequenceInitialized = true;
        }
    }

    @Override
    public AdminDashboardStats getDashboardStats() {
        AdminDashboardStats stats = accountDataMapper.getDashboardAccountStats();
        if (stats == null) {
            stats = new AdminDashboardStats();
        }
        stats.setTotalUsers(accountDataMapper.countTotalUsers());
        stats.setUnmappedAccounts(accountDataMapper.countUnmappedAccounts());
        return stats;
    }

    @Override
    public List<AdminUserSummary> getAllUsers() {
        List<AdminUserRecord> records = userDataMapper.findAllUsersWithAccountDetails();
        List<AdminUserSummary> summaries = new ArrayList<>();
        if (records != null) {
            for (AdminUserRecord record : records) {
                AdminUserSummary summary = new AdminUserSummary();
                summary.setUserId(record.getUserId());
                summary.setAccountId(record.getAccountId());
                summary.setUsername(record.getUsername());
                summary.setFirstName(record.getFirstName());
                summary.setLastName(record.getLastName());
                summary.setEmail(record.getEmail());
                summary.setPhoneNo(record.getPhoneNo());
                summary.setRoles(record.getRoles());
                summary.setAccountReference(record.getAccountReference());
                summary.setAccountStatus(record.getAccountStatus());
                summary.setCashBalance(record.getCashBalance());
                summary.setBuyingPower(record.getBuyingPower());
                summary.setCreatedOn(record.getCreatedOn());
                summaries.add(summary);
            }
        }
        return summaries;
    }

    @Override
    public List<UnassignedAccountSummary> getUnassignedAndDormantAccounts() {
        List<UnassignedAccountSummary> accounts = accountDataMapper.findUnassignedAndDormantAccounts();
        return accounts != null ? accounts : new ArrayList<>();
    }

    @Override
    @Transactional
    public void updateUserAccountStatus(UUID userId, String status) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        validateStatus(status);

        int updated = accountDataMapper.updateAccountStatusByUserId(userId, status.toUpperCase());
        if (updated == 0) {
            log.warn("Failed to update status for user {}: user or account not found", userId);
            throw new AccountNotFoundException();
        }
        log.info("Updated account status to {} for user {}", status, userId);
    }

    @Override
    @Transactional
    public void updateAccountStatus(Long accountId, String status) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID is required");
        }
        validateStatus(status);

        int updated = accountDataMapper.updateAccountStatus(accountId, status.toUpperCase());
        if (updated == 0) {
            log.warn("Failed to update status for account {}: not found", accountId);
            throw new AccountNotFoundException();
        }
        log.info("Updated account status to {} for account {}", status, accountId);
    }

    @Override
    @Transactional
    public Account createUnmappedAccount(CreateAccountRequest request) {
        String reference = (request != null && request.getAccountReference() != null && !request.getAccountReference().isBlank())
                ? request.getAccountReference().trim()
                : generateAccountReference();

        BigDecimal balance = (request != null && request.getInitialBalance() != null && request.getInitialBalance().compareTo(BigDecimal.ZERO) >= 0)
                ? request.getInitialBalance()
                : new BigDecimal("100000.00");

        accountDataMapper.insertAccount(reference, balance, balance, "ACTIVE");
        log.info("Created unmapped account with reference: {} and balance: {}", reference, balance);

        Account account = accountDataMapper.findAccountByReference(reference);
        if (account == null) {
            throw new IllegalStateException("Failed to retrieve created account: " + reference);
        }
        return account;
    }

    @Override
    @Transactional
    public List<Account> createBatchAccounts(int count, BigDecimal initialBalance) {
        int safeCount = count < 1 ? 1 : Math.min(count, 200);
        BigDecimal balance = (initialBalance != null && initialBalance.compareTo(BigDecimal.ZERO) >= 0)
                ? initialBalance
                : new BigDecimal("100000.00");

        List<Account> createdList = new ArrayList<>(safeCount);
        for (int i = 0; i < safeCount; i++) {
            String reference = generateAccountReference();
            accountDataMapper.insertAccount(reference, balance, balance, "ACTIVE");
            Account acc = accountDataMapper.findAccountByReference(reference);
            if (acc != null) {
                createdList.add(acc);
            }
        }
        log.info("Batch created {} unmapped active accounts with initial balance {}", createdList.size(), balance);
        return createdList;
    }

    private void validateStatus(String status) {
        if (status == null || !VALID_STATUSES.contains(status.toUpperCase())) {
            throw new IllegalArgumentException("Invalid account status. Expected ACTIVE, SUSPENDED, or CLOSED.");
        }
    }

    /**
     * Get the next sequence number and increment.
     * Format: ETP-220 + number (e.g., ETP-22001, ETP-22002, ..., ETP-22020, ETP-22021, etc.)
     */
    public static long getAndIncrementSequence() {
        return accountSequence.getAndIncrement();
    }

    /**
     * Generate the next account reference.
     * Uses format: ETP-220 + number (starting from existing max or 1 if none)
     */
    private String generateAccountReference() {
        long nextSequence = getAndIncrementSequence();
        return ACCOUNT_PREFIX + String.format("%02d", nextSequence);
    }
}

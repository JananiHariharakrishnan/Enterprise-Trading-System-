package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.request.WalletDepositRequest;
import com.tradingsystem.dto.response.WalletDepositResponse;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.InvalidWalletPasswordException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.User;
import com.tradingsystem.service.PasswordVerifier;
import com.tradingsystem.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);

    private final AccountDataMapper accountDataMapper;
    private final UserDataMapper userDataMapper;
    private final PasswordVerifier passwordVerifier;

    public WalletServiceImpl(
            AccountDataMapper accountDataMapper,
            UserDataMapper userDataMapper,
            PasswordVerifier passwordVerifier
    ) {
        this.accountDataMapper = accountDataMapper;
        this.userDataMapper = userDataMapper;
        this.passwordVerifier = passwordVerifier;
    }

    @Override
    public Account getWalletDetails(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        Account account = accountDataMapper.findAccountById(accountId);
        if (account == null) {
            throw new AccountNotFoundException();
        }
        return account;
    }

    @Override
    @Transactional
    public WalletDepositResponse depositFunds(Long accountId, WalletDepositRequest request) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero");
        }

        Account account = accountDataMapper.findAccountById(accountId);
        if (account == null) {
            log.warn("Deposit failed: account {} not found", accountId);
            throw new AccountNotFoundException();
        }

        if (account.getStatus() != com.tradingsystem.enums.AccountStatus.ACTIVE) {
            log.warn("Deposit rejected: account {} is in status {}", accountId, account.getStatus());
            throw new AccountNotActiveException();
        }

        User user = userDataMapper.findUserByAccountId(accountId);
        if (user == null) {
            log.warn("Deposit failed: no user associated with account {}", accountId);
            throw new AccountNotFoundException();
        }

        // Verify password confirmation
        boolean validPassword = passwordVerifier.verifyPassword(user.getUsername(), request.getPassword());
        if (!validPassword) {
            log.warn("Deposit rejected: password confirmation failed for user {}", user.getUsername());
            throw new InvalidWalletPasswordException();
        }

        BigDecimal previousBalance = account.getCashBalance();
        BigDecimal depositAmount = request.getAmount();

        accountDataMapper.depositFunds(accountId, depositAmount);
        accountDataMapper.insertBalanceHistory(accountId, "DEPOSIT", depositAmount);

        Account updated = accountDataMapper.findAccountById(accountId);
        log.info("Successfully deposited {} to account {}. New cash balance: {}",
                depositAmount, accountId, updated.getCashBalance());

        return new WalletDepositResponse(
                accountId,
                updated.getAccountReference(),
                previousBalance,
                depositAmount,
                updated.getCashBalance(),
                updated.getBuyingPower(),
                "Funds deposited successfully."
        );
    }
}

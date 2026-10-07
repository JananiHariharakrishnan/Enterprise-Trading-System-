package com.tradingsystem.service.impl;

import com.tradingsystem.dto.request.WalletDepositRequest;
import com.tradingsystem.dto.response.WalletDepositResponse;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.InvalidWalletPasswordException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.User;
import com.tradingsystem.service.Impl.WalletServiceImpl;
import com.tradingsystem.service.PasswordVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private AccountDataMapper accountDataMapper;

    @Mock
    private UserDataMapper userDataMapper;

    @Mock
    private PasswordVerifier passwordVerifier;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void getWalletDetails_accountExists_returnsAccount() {
        Account account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("1000.00"));
        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);

        Account result = walletService.getWalletDetails(1001L);
        assertNotNull(result);
        assertEquals(1001L, result.getAccountId());
    }

    @Test
    void getWalletDetails_notFound_throwsAccountNotFoundException() {
        when(accountDataMapper.findAccountById(9999L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () ->
                walletService.getWalletDetails(9999L));
    }

    @Test
    void depositFunds_validPassword_depositsAndRecordsHistory() {
        Long accountId = 1001L;
        BigDecimal initialBalance = new BigDecimal("2000.00");
        BigDecimal depositAmount = new BigDecimal("500.00");

        Account existing = new Account();
        existing.setAccountId(accountId);
        existing.setAccountReference("ETP-1001");
        existing.setCashBalance(initialBalance);
        existing.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        User user = new User();
        user.setAccountId(accountId);
        user.setUsername("testuser");

        Account updated = new Account();
        updated.setAccountId(accountId);
        updated.setAccountReference("ETP-1001");
        updated.setCashBalance(new BigDecimal("2500.00"));
        updated.setBuyingPower(new BigDecimal("2500.00"));
        updated.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        when(accountDataMapper.findAccountById(accountId))
                .thenReturn(existing)
                .thenReturn(updated);
        when(userDataMapper.findUserByAccountId(accountId)).thenReturn(user);
        when(passwordVerifier.verifyPassword("testuser", "correctPassword")).thenReturn(true);

        WalletDepositRequest req = new WalletDepositRequest(depositAmount, "correctPassword");
        WalletDepositResponse response = walletService.depositFunds(accountId, req);

        assertNotNull(response);
        assertEquals(accountId, response.getAccountId());
        assertEquals(initialBalance, response.getPreviousBalance());
        assertEquals(depositAmount, response.getDepositedAmount());
        assertEquals(new BigDecimal("2500.00"), response.getNewCashBalance());

        verify(accountDataMapper).depositFunds(accountId, depositAmount);
        verify(accountDataMapper).insertBalanceHistory(accountId, "DEPOSIT", depositAmount);
    }

    @Test
    void depositFunds_wrongPassword_throwsInvalidWalletPasswordException() {
        Long accountId = 1001L;
        Account existing = new Account();
        existing.setAccountId(accountId);
        existing.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        User user = new User();
        user.setAccountId(accountId);
        user.setUsername("testuser");

        when(accountDataMapper.findAccountById(accountId)).thenReturn(existing);
        when(userDataMapper.findUserByAccountId(accountId)).thenReturn(user);
        when(passwordVerifier.verifyPassword("testuser", "wrongPassword")).thenReturn(false);

        WalletDepositRequest req = new WalletDepositRequest(new BigDecimal("500.00"), "wrongPassword");

        assertThrows(InvalidWalletPasswordException.class, () ->
                walletService.depositFunds(accountId, req));

        verify(accountDataMapper, never()).depositFunds(any(), any());
    }

    @Test
    void depositFunds_accountSuspended_throwsAccountNotActiveException() {
        Long accountId = 1001L;
        Account suspended = new Account();
        suspended.setAccountId(accountId);
        suspended.setStatus(com.tradingsystem.enums.AccountStatus.SUSPENDED);

        when(accountDataMapper.findAccountById(accountId)).thenReturn(suspended);

        WalletDepositRequest req = new WalletDepositRequest(new BigDecimal("500.00"), "pass");

        assertThrows(AccountNotActiveException.class, () ->
                walletService.depositFunds(accountId, req));
    }
}

package com.tradingsystem.service.impl;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.PositionResponse;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Position;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.Impl.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountDataMapper accountDataMapper;

    @Mock
    private CurrentUser currentUser;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService =
                new AccountServiceImpl(
                        accountDataMapper,
                        currentUser
                );
    }

    @Test
    void getAccount_returnsAccount_whenAuthorized() {

        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(1001L);

        when(accountDataMapper.findAccountHolderName(1001L))
                .thenReturn("Ananya Iyer");

        AccountResponse response =
                accountService.getAccount(1001L);

        assertEquals(
                1001L,
                response.getId()
        );

        assertEquals(
                "ACC-1001",
                response.getAccountId()
        );

        assertEquals(
                "Ananya Iyer",
                response.getHolderName()
        );

        assertEquals(
                new BigDecimal("100000.00"),
                response.getCashBalance()
        );

        assertEquals(
                "ACTIVE",
                response.getStatus()
        );
    }

    @Test
    void getAccount_throwsAcc404_whenAccountDoesNotExist() {

        when(accountDataMapper.findAccountById(9999L))
                .thenReturn(null);

        assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getAccount(9999L)
        );

        verifyNoInteractions(currentUser);
    }

    @Test
    void getAccount_throwsAcc403_whenTokenCannotReachAccount() {

        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(2002L);

        assertThrows(
                AccountNotActiveException.class,
                () -> accountService.getAccount(1001L)
        );

        verify(
                accountDataMapper,
                never()
        ).findAccountHolderName(1001L);
    }

    @Test
    void getAccount_throwsAcc403_whenAccountIsInactive() {

        Account account = activeAccount(1001L);
        account.setStatus(
                com.tradingsystem.enums.AccountStatus.SUSPENDED
        );

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        assertThrows(
                AccountNotActiveException.class,
                () -> accountService.getAccount(1001L)
        );

        verifyNoInteractions(currentUser);
    }

    @Test
    void getBalance_returnsBalance_whenAuthorized() {

        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(1001L);

        BalanceResponse response =
                accountService.getBalance(1001L);

        assertEquals(
                1001L,
                response.getAccountId()
        );

        assertEquals(
                new BigDecimal("100000.00"),
                response.getCashBalance()
        );

        assertNotNull(response.getAsOf());
    }

    @Test
    void getBalance_throwsAcc404_whenAccountDoesNotExist() {

        when(accountDataMapper.findAccountById(9999L))
                .thenReturn(null);

        assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getBalance(9999L)
        );
    }

    @Test
    void getBalance_throwsAcc403_whenTokenCannotReachAccount() {

        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(2002L);

        assertThrows(
                AccountNotActiveException.class,
                () -> accountService.getBalance(1001L)
        );
    }

    @Test
    void getPositions_returnsPositions_whenAuthorized() {

        Account account = activeAccount(1001L);

        Position position = new Position();
        position.setAccountId(1001L);
        position.setSymbol("AAPL");
        position.setQuantity(15);
        position.setAverageCost(
                new BigDecimal("171.50")
        );

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(1001L);

        when(
                accountDataMapper.findPositionsByAccountId(1001L)
        ).thenReturn(List.of(position));

        List<PositionResponse> result =
                accountService.getPositions(1001L);

        assertEquals(1, result.size());

        assertEquals(
                "AAPL",
                result.get(0).getSymbol()
        );

        assertEquals(
                15,
                result.get(0).getQuantity()
        );

        assertEquals(
                new BigDecimal("171.50"),
                result.get(0).getAverageCost()
        );
    }

    @Test
    void getPositions_returnsEmptyList_whenNoPositions() {

        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(1001L);

        when(
                accountDataMapper.findPositionsByAccountId(1001L)
        ).thenReturn(List.of());

        List<PositionResponse> result =
                accountService.getPositions(1001L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getPositions_throwsAcc404_whenAccountDoesNotExist() {

        when(accountDataMapper.findAccountById(9999L))
                .thenReturn(null);

        assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getPositions(9999L)
        );

        verify(
                accountDataMapper,
                never()
        ).findPositionsByAccountId(9999L);
    }

    @Test
    void getPositions_throwsAcc403_whenTokenCannotReachAccount() {

        Account account = activeAccount(1001L);

        when(accountDataMapper.findAccountById(1001L))
                .thenReturn(account);

        when(currentUser.getAccountId())
                .thenReturn(2002L);

        assertThrows(
                AccountNotActiveException.class,
                () -> accountService.getPositions(1001L)
        );

        verify(
                accountDataMapper,
                never()
        ).findPositionsByAccountId(1001L);
    }

    private Account activeAccount(Long id) {

        Account account = new Account();

        account.setAccountId(id);
        account.setAccountReference(
                "ACC-" + id
        );
        account.setCashBalance(
                new BigDecimal("100000.00")
        );
        account.setBuyingPower(
                new BigDecimal("100000.00")
        );
        account.setStatus(
                com.tradingsystem.enums.AccountStatus.ACTIVE
        );
        account.setVersion(1);
        account.setLastUpdated(
                LocalDateTime.of(
                        2026,
                        9,
                        1,
                        10,
                        0
                )
        );

        return account;
    }
}
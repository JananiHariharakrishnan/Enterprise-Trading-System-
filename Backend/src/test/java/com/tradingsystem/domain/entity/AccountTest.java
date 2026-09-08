package com.tradingsystem.domain.entity;

import com.tradingsystem.domain.enums.AccountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void activeAccountReportsActiveStatus() {
        Account account = new Account();
        account.setStatus(AccountStatus.ACTIVE);

        assertTrue(account.isActive());
    }

    @Test
    void suspendedAccountDoesNotReportActiveStatus() {
        Account account = new Account();
        account.setStatus(AccountStatus.SUSPENDED);

        assertFalse(account.isActive());
    }

    @Test
    void closedAccountDoesNotReportActiveStatus() {
        Account account = new Account();
        account.setStatus(AccountStatus.CLOSED);

        assertFalse(account.isActive());
    }

    @Test
    void creditIncreasesCashBalance() {
        Account account = new Account();
        account.setCashBalance(new BigDecimal("100.00"));

        account.credit(new BigDecimal("25.00"));

        assertEquals(
                new BigDecimal("125.00"),
                account.getCashBalance()
        );
    }

    @Test
    void debitDecreasesCashBalance() {
        Account account = new Account();
        account.setCashBalance(new BigDecimal("100.00"));

        account.debit(new BigDecimal("25.00"));

        assertEquals(
                new BigDecimal("75.00"),
                account.getCashBalance()
        );
    }

    @Test
    void accountCannotDebitMoreThanCashBalance() {
        Account account = new Account();
        account.setCashBalance(new BigDecimal("100.00"));

        assertThrows(
                IllegalArgumentException.class,
                () -> account.debit(new BigDecimal("100.01"))
        );

        assertEquals(
                new BigDecimal("100.00"),
                account.getCashBalance()
        );
    }

    @Test
    void accountCanAffordExactCashBalance() {
        Account account = new Account();
        account.setCashBalance(new BigDecimal("100.00"));

        assertTrue(account.canAfford(new BigDecimal("100.00")));
    }

    @Test
    void accountCannotAffordMoreThanCashBalance() {
        Account account = new Account();
        account.setCashBalance(new BigDecimal("100.00"));

        assertFalse(account.canAfford(new BigDecimal("100.01")));
    }

    @Test
    void repeatedCreditAndDebitDoesNotDriftMoney() {
        Account account = new Account();
        account.setCashBalance(new BigDecimal("1000.00"));

        for (int i = 0; i < 1000; i++) {
            account.credit(new BigDecimal("0.10"));
            account.debit(new BigDecimal("0.10"));
        }

        assertEquals(
                new BigDecimal("1000.00"),
                account.getCashBalance()
        );
    }
}
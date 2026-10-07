package com.tradingsystem.service.impl;

import com.tradingsystem.dto.request.CreateAccountRequest;
import com.tradingsystem.dto.response.AdminDashboardStats;
import com.tradingsystem.dto.response.AdminUserSummary;
import com.tradingsystem.dto.response.UnassignedAccountSummary;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.AdminUserRecord;
import com.tradingsystem.service.Impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private AccountDataMapper accountDataMapper;

    @Mock
    private UserDataMapper userDataMapper;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    void getDashboardStats_returnsAggregatedMetrics() {
        AdminDashboardStats dbStats = new AdminDashboardStats(
                new BigDecimal("2500000.00"), 10, 8, 1, 1, 0, 0
        );
        when(accountDataMapper.getDashboardAccountStats()).thenReturn(dbStats);
        when(accountDataMapper.countTotalUsers()).thenReturn(7L);
        when(accountDataMapper.countUnmappedAccounts()).thenReturn(3L);

        AdminDashboardStats result = adminService.getDashboardStats();

        assertNotNull(result);
        assertEquals(new BigDecimal("2500000.00"), result.getTotalMoney());
        assertEquals(10, result.getTotalAccounts());
        assertEquals(8, result.getOpenAccounts());
        assertEquals(1, result.getSuspendedAccounts());
        assertEquals(1, result.getClosedAccounts());
        assertEquals(7, result.getTotalUsers());
        assertEquals(3, result.getUnmappedAccounts());
    }

    @Test
    void getAllUsers_returnsMappedSummaries() {
        AdminUserRecord record = new AdminUserRecord();
        UUID userId = UUID.randomUUID();
        record.setUserId(userId);
        record.setAccountId(1001L);
        record.setUsername("alice");
        record.setFirstName("Alice");
        record.setLastName("Wonderland");
        record.setEmail("alice@example.com");
        record.setRolesString("{CUSTOMER}");
        record.setAccountReference("ETP-1001");
        record.setAccountStatus("ACTIVE");
        record.setCashBalance(new BigDecimal("5000.00"));
        record.setBuyingPower(new BigDecimal("5000.00"));
        record.setCreatedOn(OffsetDateTime.now());

        when(userDataMapper.findAllUsersWithAccountDetails()).thenReturn(List.of(record));

        List<AdminUserSummary> users = adminService.getAllUsers();

        assertEquals(1, users.size());
        AdminUserSummary summary = users.get(0);
        assertEquals(userId, summary.getUserId());
        assertEquals("alice", summary.getUsername());
        assertEquals("ACTIVE", summary.getAccountStatus());
        assertEquals(List.of("CUSTOMER"), summary.getRoles());
    }

    @Test
    void getUnassignedAndDormantAccounts_returnsList() {
        UnassignedAccountSummary acc = new UnassignedAccountSummary();
        acc.setAccountId(200L);
        acc.setAccountReference("ETP-22010");
        acc.setUnassigned(true);
        acc.setStatus("ACTIVE");

        when(accountDataMapper.findUnassignedAndDormantAccounts()).thenReturn(List.of(acc));

        List<UnassignedAccountSummary> result = adminService.getUnassignedAndDormantAccounts();

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getAccountId());
        assertTrue(result.get(0).isUnassigned());
    }

    @Test
    void updateUserAccountStatus_validStatus_updatesSuccessfully() {
        UUID userId = UUID.randomUUID();
        when(accountDataMapper.updateAccountStatusByUserId(eq(userId), eq("SUSPENDED"))).thenReturn(1);

        assertDoesNotThrow(() -> adminService.updateUserAccountStatus(userId, "SUSPENDED"));
        verify(accountDataMapper).updateAccountStatusByUserId(eq(userId), eq("SUSPENDED"));
    }

    @Test
    void updateUserAccountStatus_notFound_throwsAccountNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(accountDataMapper.updateAccountStatusByUserId(eq(userId), eq("CLOSED"))).thenReturn(0);

        assertThrows(AccountNotFoundException.class, () ->
                adminService.updateUserAccountStatus(userId, "CLOSED"));
    }

    @Test
    void updateAccountStatus_invalidStatus_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                adminService.updateAccountStatus(1001L, "INVALID_STATUS"));
    }

    @Test
    void createUnmappedAccount_createsAndReturnsAccount() {
        CreateAccountRequest req = new CreateAccountRequest("ETP-POOL-XYZ", new BigDecimal("75000.00"));

        Account createdAccount = new Account();
        createdAccount.setAccountId(500L);
        createdAccount.setAccountReference("ETP-POOL-XYZ");
        createdAccount.setCashBalance(new BigDecimal("75000.00"));
        createdAccount.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        when(accountDataMapper.insertAccount(eq("ETP-POOL-XYZ"), eq(new BigDecimal("75000.00")),
                eq(new BigDecimal("75000.00")), eq("ACTIVE"))).thenReturn(1);
        when(accountDataMapper.findAccountByReference("ETP-POOL-XYZ")).thenReturn(createdAccount);

        Account result = adminService.createUnmappedAccount(req);

        assertNotNull(result);
        assertEquals("ETP-POOL-XYZ", result.getAccountReference());
        assertEquals(new BigDecimal("75000.00"), result.getCashBalance());
    }

    @Test
    void createBatchAccounts_createsSpecifiedNumberOfAccounts() {
        Account dummy = new Account();
        dummy.setAccountId(123L);
        dummy.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);
        dummy.setCashBalance(new BigDecimal("100000.00"));
        dummy.setAccountReference("ETP-22020");

        when(accountDataMapper.insertAccount(any(), any(), any(), eq("ACTIVE"))).thenReturn(1);
        when(accountDataMapper.findAccountByReference(any())).thenReturn(dummy);

        List<Account> accounts = adminService.createBatchAccounts(3, new BigDecimal("100000.00"));

        assertEquals(3, accounts.size());
        verify(accountDataMapper, times(3)).insertAccount(argThat(ref -> ref.startsWith("ETP-")), 
                eq(new BigDecimal("100000.00")), eq(new BigDecimal("100000.00")), eq("ACTIVE"));
    }
}

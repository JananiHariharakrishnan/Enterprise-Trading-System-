package com.tradingsystem.service.impl;

import com.tradingsystem.mapper.UserDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.service.AdminService;
import com.tradingsystem.service.Impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplAccountPoolTest {

    @Mock
    private UserDataMapper userDataMapper;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getAvailableAccountId_returnsExistingPoolAccount() {
        when(userDataMapper.findAvailableAccountId()).thenReturn(15L);

        assertEquals(15L, userService.getAvailableAccountId());
        verify(adminService, never()).createUnmappedAccount(null);
    }

    @Test
    void getAvailableAccountId_createsAccountWhenPoolIsEmpty() {
        when(userDataMapper.findAvailableAccountId()).thenReturn(null);
        Account created = new Account();
        created.setAccountId(44L);
        when(adminService.createUnmappedAccount(null)).thenReturn(created);

        assertEquals(44L, userService.getAvailableAccountId());
        verify(adminService).createUnmappedAccount(null);
    }
}

package com.tradingsystem.service.impl;

import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.Impl.OrderServiceImpl;
import com.tradingsystem.service.OrderLogic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderLogic orderLogic;

    @Mock
    private OrderDataMapper orderDataMapper;

    @Mock
    private AccountDataMapper accountDataMapper;

    @Mock
    private CurrentUser currentUser;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {

        orderService =
                new OrderServiceImpl(
                        orderLogic,
                        orderDataMapper,
                        accountDataMapper,
                        currentUser
                );
    }

    @Test
void getOrderHistory_returnsOrders_whenAuthorized() {
    Account account = activeAccount(1001L);

    when(accountDataMapper.findAccountById(1001L))
            .thenReturn(account);

    when(currentUser.getAccountId())
            .thenReturn(1001L);

    when(orderDataMapper.findOrderHistory(
            eq(1001L),
            isNull(),
            isNull(),
            isNull()
    )).thenReturn(java.util.List.of());

    var result = orderService.getOrderHistory(
            1001L,
            null,
            null,
            null
    );

    assertNotNull(result);
    assertTrue(result.isEmpty());
}

    @Test
    void getOrderHistory_throwsAcc404_whenAccountDoesNotExist() {

        when(
                accountDataMapper.findAccountById(9999L)
        ).thenReturn(null);

        assertThrows(
                AccountNotFoundException.class,
                () -> orderService.getOrderHistory(
                        9999L,
                        null,
                        null,
                        null
                )
        );

        verify(
                orderDataMapper,
                never()
        ).findOrderHistory(
                anyLong(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void getOrderHistory_throwsAcc403_whenTokenCannotReachAccount() {

        Account account = activeAccount(1001L);

        when(
                accountDataMapper.findAccountById(1001L)
        ).thenReturn(account);

        when(
                currentUser.getAccountId()
        ).thenReturn(2002L);

        assertThrows(
                AccountNotActiveException.class,
                () -> orderService.getOrderHistory(
                        1001L,
                        null,
                        null,
                        null
                )
        );

        verify(
                orderDataMapper,
                never()
        ).findOrderHistory(
                anyLong(),
                any(),
                any(),
                any()
        );
    }

    private Account activeAccount(Long id) {

        Account account = new Account();

        account.setAccountId(id);
        account.setAccountReference(
                "ACC-" + id
        );
        account.setStatus(
                com.tradingsystem.enums.AccountStatus.ACTIVE
        );

        return account;
    }
}
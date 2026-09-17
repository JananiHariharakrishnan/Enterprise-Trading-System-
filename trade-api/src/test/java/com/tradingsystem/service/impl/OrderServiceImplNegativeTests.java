package com.tradingsystem.service.impl;

import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.InvalidOrderException;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.model.Account;
import com.tradingsystem.service.Impl.OrderServiceImpl;
import com.tradingsystem.service.command.PlaceOrderCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceImplNegativeTests {

    private com.tradingsystem.service.OrderLogic orderLogic;
    private com.tradingsystem.mapper.OrderDataMapper orderDataMapper;
    private com.tradingsystem.mapper.AccountDataMapper accountDataMapper;
    private com.tradingsystem.security.CurrentUser currentUser;
    private com.tradingsystem.messaging.OrderEventPublisher orderEventPublisher;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderLogic = mock(com.tradingsystem.service.OrderLogic.class);
        orderDataMapper = mock(com.tradingsystem.mapper.OrderDataMapper.class);
        accountDataMapper = mock(com.tradingsystem.mapper.AccountDataMapper.class);
        currentUser = mock(com.tradingsystem.security.CurrentUser.class);
        orderEventPublisher = mock(com.tradingsystem.messaging.OrderEventPublisher.class);

        orderService = new OrderServiceImpl(
                orderLogic,
                orderDataMapper,
                accountDataMapper,
                currentUser,
                orderEventPublisher
        );
    }

    @Test
    void placeOrder_duplicateIdempotency_throwsDuplicateOrder() {
        PlaceOrderCommand cmd = new PlaceOrderCommand();
        cmd.setAccountId(1001L);
        cmd.setSymbol("AAPL");
        cmd.setSide(null);

        Account account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("1000"));
        account.setBuyingPower(new BigDecimal("1000"));
        account.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);

        when(accountDataMapper.findAccountById(anyLong())).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(true);

        assertThrows(DuplicateOrderException.class, () -> orderService.placeOrder(cmd));
    }

    @Test
    void placeOrder_updateAccountFails_throwsOrderStateConflict() {
        PlaceOrderCommand cmd = new PlaceOrderCommand();
        cmd.setAccountId(1001L);
        cmd.setSymbol("AAPL");
        cmd.setSide(com.tradingsystem.enums.OrderSide.BUY);
        cmd.setOrderType(com.tradingsystem.enums.OrderType.MARKET);
        cmd.setQuantity(1);
        cmd.setPrice(new BigDecimal("1.00"));
        cmd.setIdempotencyKey("k");

        Account account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("1000"));
        account.setBuyingPower(new BigDecimal("1000"));
        account.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);
        account.setVersion(1);

        when(accountDataMapper.findAccountById(anyLong())).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(0);

        assertThrows(OrderStateConflictException.class, () -> orderService.placeOrder(cmd));
    }

    @Test
    void placeOrder_invalidSide_throwsInvalidOrder() {
        PlaceOrderCommand cmd = new PlaceOrderCommand();
        cmd.setAccountId(1001L);
        cmd.setSymbol("AAPL");
        // leave side null to trigger invalid order side branch
        cmd.setSide(null);
        cmd.setOrderType(com.tradingsystem.enums.OrderType.MARKET);
        cmd.setQuantity(1);
        cmd.setPrice(new BigDecimal("1.00"));
        cmd.setIdempotencyKey("k2");

        Account account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("1000"));
        account.setBuyingPower(new BigDecimal("1000"));
        account.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);
        account.setVersion(1);

        when(accountDataMapper.findAccountById(anyLong())).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(1);

        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(cmd));
    }
}

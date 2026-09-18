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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
        // NOTE: With the fix for double-charging bug, balance is NO LONGER updated at placement.
        // Account updates only happen during execution (ExecutionProcessingService).
        // This test is no longer valid and can be removed or repurposed.
        // Keeping it as a reminder of the old behavior.
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
        // OrderLogic.placeOrder() should throw InvalidOrderException for invalid side
        doThrow(new InvalidOrderException("Invalid order side"))
                .when(orderLogic)
                .placeOrder(any(), any(), any(), any());

        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(cmd));
    }
}

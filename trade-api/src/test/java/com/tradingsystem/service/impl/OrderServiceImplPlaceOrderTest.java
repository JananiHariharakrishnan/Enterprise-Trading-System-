package com.tradingsystem.service.impl;

import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.model.Account;
import com.tradingsystem.service.Impl.OrderServiceImpl;
import com.tradingsystem.service.command.PlaceOrderCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceImplPlaceOrderTest {

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

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void placeOrder_happyPath_commitsAndPublishesEvent() {
        // Arrange
        PlaceOrderCommand cmd = new PlaceOrderCommand();
        cmd.setAccountId(1001L);
        cmd.setSymbol("AAPL");
        cmd.setSide(OrderSide.BUY);
        cmd.setOrderType(com.tradingsystem.enums.OrderType.MARKET);
        cmd.setQuantity(10);
        cmd.setPrice(new BigDecimal("100.00"));
        cmd.setIdempotencyKey("key-1");

        Account account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("100000.00"));
        account.setBuyingPower(new BigDecimal("100000.00"));
        account.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);
        account.setVersion(1);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(1001L);
        when(orderDataMapper.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(1);
        when(orderDataMapper.insertOrder(any())).thenReturn(1);

        // Make transaction synchronization active so publishAfterCommit can register
        TransactionSynchronizationManager.initSynchronization();

        // Act
        OrderResponse response = orderService.placeOrder(cmd);

        // Simulate commit to invoke registered synchronizations
        for (org.springframework.transaction.support.TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }

        // Assert
        assertNotNull(response);
        assertEquals(com.tradingsystem.enums.OrderStatus.NEW, response.getStatus());
        verify(orderDataMapper, times(1)).updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("100000.00"),
            new BigDecimal("99000.00"),
            1
        );
        verify(orderEventPublisher, times(1)).publishOrderPlaced(any());
    }
}

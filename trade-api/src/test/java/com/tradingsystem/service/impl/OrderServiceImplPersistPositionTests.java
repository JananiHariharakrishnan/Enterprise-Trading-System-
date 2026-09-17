package com.tradingsystem.service.impl;

import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.service.Impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceImplPersistPositionTests {

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
    void persistPosition_insertFails_throwsIllegalState() {
        Order o = new Order();
        o.setAccountId(1L);
        o.setSymbol("X");
        o.setSide(OrderSide.BUY);
        o.setQuantity(1);
        o.setPrice(new BigDecimal("1.00"));
        o.setIdempotencyKey("k1");

        Account acct = new Account();
        acct.setAccountId(1L);
        acct.setCashBalance(new BigDecimal("1000"));
        acct.setBuyingPower(new BigDecimal("1000"));
        acct.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);
        acct.setVersion(1);

        when(accountDataMapper.findAccountById(anyLong())).thenReturn(acct);
        when(currentUser.getAccountId()).thenReturn(1L);
        when(orderDataMapper.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(orderDataMapper.findInstrumentBySymbol(anyString())).thenReturn(new Instrument(){ { setTradable(true); } });
        when(orderDataMapper.findPosition(anyLong(), anyString())).thenReturn(null);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyInt())).thenReturn(1);
        when(orderDataMapper.insertPosition(anyLong(), anyString(), anyInt(), any(BigDecimal.class))).thenReturn(0);

        // insertPosition failing should cause IllegalStateException during placeOrder
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> orderService.placeOrder(new com.tradingsystem.service.command.PlaceOrderCommand() {{
                    setAccountId(1L); setSymbol("X"); setSide(OrderSide.BUY); setQuantity(1); setPrice(new BigDecimal("1.00")); setIdempotencyKey("k1");
                }}));
    }

    @Test
    void persistPosition_updatePositionFails_throwsOrderStateConflict() {
        Order o = new Order();
        o.setAccountId(1L);
        o.setSymbol("X");
        o.setSide(OrderSide.SELL);
        o.setQuantity(1);
        o.setPrice(new BigDecimal("1.00"));
        o.setIdempotencyKey("k2");

        Account acct = new Account();
        acct.setAccountId(1L);
        acct.setCashBalance(new BigDecimal("1000"));
        acct.setBuyingPower(new BigDecimal("1000"));
        acct.setStatus(com.tradingsystem.enums.AccountStatus.ACTIVE);
        acct.setVersion(1);

        Position pos = new Position();
        pos.setAccountId(1L);
        pos.setSymbol("X");
        pos.setQuantity(5);
        pos.setAverageCost(new BigDecimal("1.00"));

        when(accountDataMapper.findAccountById(anyLong())).thenReturn(acct);
        when(currentUser.getAccountId()).thenReturn(1L);
        when(orderDataMapper.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(orderDataMapper.findInstrumentBySymbol(anyString())).thenReturn(new Instrument(){ { setTradable(true); } });
        when(orderDataMapper.findPosition(anyLong(), anyString())).thenReturn(pos);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyInt())).thenReturn(1);
        when(orderDataMapper.updatePosition(anyLong(), anyString(), anyInt(), any(BigDecimal.class))).thenReturn(0);

        assertThrows(OrderStateConflictException.class, () -> orderService.placeOrder(new com.tradingsystem.service.command.PlaceOrderCommand() {{
            setAccountId(1L); setSymbol("X"); setSide(OrderSide.SELL); setQuantity(1); setPrice(new BigDecimal("1.00")); setIdempotencyKey("k2");
        }}));
    }
}

package com.tradingsystem.service.impl;

import com.tradingsystem.ai.signal.PortfolioContext;
import com.tradingsystem.ai.signal.SignalEngine;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.AccountStatus;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.enums.OrderType;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.InvalidOrderException;
import com.tradingsystem.exception.OrderNotFoundException;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.messaging.OrderEventPublisher;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.Impl.OrderServiceImpl;
import com.tradingsystem.service.OrderLogic;
import com.tradingsystem.service.command.PlaceOrderCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplGapsTest {

    private OrderLogic orderLogic;
    private OrderDataMapper orderDataMapper;
    private AccountDataMapper accountDataMapper;
    private CurrentUser currentUser;
    private OrderEventPublisher orderEventPublisher;
    private SignalEngine signalEngine;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderLogic = mock(OrderLogic.class);
        orderDataMapper = mock(OrderDataMapper.class);
        accountDataMapper = mock(AccountDataMapper.class);
        currentUser = mock(CurrentUser.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        signalEngine = mock(SignalEngine.class);
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
    void placeOrder_rejectsNullCommandAndBrokenBalances() {
        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(null));

        Account account = activeAccount();
        account.setCashBalance(null);
        wireAuth(account);
        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(command("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN)));

        account.setCashBalance(new BigDecimal("1000"));
        account.setBuyingPower(null);
        assertThrows(InvalidOrderException.class, () -> orderService.placeOrder(command("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN)));
    }

    @Test
    void placeOrder_convertsNsLimitBuyAndPublishesPortfolioSignal() {
        ReflectionTestUtils.setField(orderService, "signalEngine", signalEngine);
        Account account = activeAccount();
        wireAuth(account);
        Instrument instrument = instrument("RELIANCE.NS", "INR");
        Position position = new Position();
        position.setQuantity(8);
        position.setAverageCost(new BigDecimal("90"));

        when(orderDataMapper.findInstrumentBySymbol("RELIANCE.NS")).thenReturn(instrument);
        when(orderDataMapper.findPosition(1001L, "RELIANCE.NS")).thenReturn(position);
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(1);
        when(orderDataMapper.insertOrder(any())).thenReturn(1);
        beginTransaction();

        OrderResponse response = orderService.placeOrder(
                command("RELIANCE.NS", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("10.00"))
        );
        fireAfterCommit();

        ArgumentCaptor<Order> stored = ArgumentCaptor.forClass(Order.class);
        verify(orderDataMapper).insertOrder(stored.capture());
        assertEquals(0, new BigDecimal("960.00").compareTo(stored.getValue().getExecutorPrice()));
        assertEquals("INR", stored.getValue().getOriginalCurrency());
        verify(orderDataMapper).updateAccountBalancesWithVersion(
                eq(1001L),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("980.00")),
                eq(3)
        );

        assertEquals("INR", response.getOriginalCurrency());
        assertEquals(0, new BigDecimal("960.00").compareTo(response.getOriginalPrice()));
        assertEquals(OrderStatus.NEW, response.getStatus());

        ArgumentCaptor<PortfolioContext> portfolio = ArgumentCaptor.forClass(PortfolioContext.class);
        verify(signalEngine).generateSignalForSymbol(eq("RELIANCE.NS"), eq(960.0), portfolio.capture(), eq(true));
        assertEquals(8, portfolio.getValue().getCurrentHoldingQuantity());
        assertEquals(new BigDecimal("90"), portfolio.getValue().getCurrentAverageCost());
        assertEquals("BUY", portfolio.getValue().getOrderSide());
        verify(orderEventPublisher).publishOrderPlaced(any());
    }

    @Test
    void placeOrder_marketOrderSkipsReservationAndSwallowsSignalFailure() {
        ReflectionTestUtils.setField(orderService, "signalEngine", signalEngine);
        wireAuth(activeAccount());
        when(orderDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(instrument("AAPL", "USD"));
        when(orderDataMapper.findPosition(1001L, "AAPL")).thenReturn(null);
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderDataMapper.insertOrder(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setCreatedOn(null);
            order.setSide(null);
            order.setOrderType(null);
            order.setStatus(null);
            return 1;
        });
        doThrow(new RuntimeException("signal down"))
                .when(signalEngine).generateSignalForSymbol(any(), any(), any(), eq(true));
        beginTransaction();

        OrderResponse response = orderService.placeOrder(command("AAPL", OrderSide.BUY, OrderType.MARKET, null));
        fireAfterCommit();

        assertNotNull(response.getOrderId());
        verify(orderDataMapper, never()).updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt());
        verify(orderEventPublisher).publishOrderPlaced(any());
        verify(signalEngine).generateSignalForSymbol(eq("AAPL"), isNull(), any(PortfolioContext.class), eq(true));
    }

    @Test
    void placeOrder_sellLimitDoesNotReserveBuyingPower() {
        wireAuth(activeAccount());
        when(orderDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(instrument("AAPL", "USD"));
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderDataMapper.insertOrder(any())).thenReturn(1);
        beginTransaction();

        orderService.placeOrder(command("AAPL", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("15")));

        verify(orderDataMapper, never()).updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt());
    }

    @Test
    void placeOrder_rejectsConcurrentBalanceUpdateAndBadInserts() {
        wireAuth(activeAccount());
        when(orderDataMapper.findInstrumentBySymbol(anyString())).thenReturn(instrument("AAPL", "USD"));
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(0);

        assertThrows(OrderStateConflictException.class, () ->
                orderService.placeOrder(command("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN)));
        verify(orderDataMapper, never()).insertOrder(any());

        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(1);
        when(orderDataMapper.insertOrder(any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () ->
                orderService.placeOrder(command("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN)));
    }

    @Test
    void placeOrder_mapsConstraintViolationsAndRequiresATransaction() {
        wireAuth(activeAccount());
        when(orderDataMapper.findInstrumentBySymbol(anyString())).thenReturn(instrument("AAPL", "USD"));
        when(orderDataMapper.existsByIdempotencyKey(any())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("dup", new RuntimeException("idempotency_key already exists")))
                .when(orderDataMapper).insertOrder(any());

        assertThrows(DuplicateOrderException.class, () ->
                orderService.placeOrder(command("AAPL", OrderSide.SELL, OrderType.MARKET, null)));

        doThrow(new DataIntegrityViolationException("dup", new RuntimeException("uq_orders violated")))
                .when(orderDataMapper).insertOrder(any());
        assertThrows(DuplicateOrderException.class, () ->
                orderService.placeOrder(command("AAPL", OrderSide.SELL, OrderType.MARKET, null)));

        doThrow(new DataIntegrityViolationException("other constraint"))
                .when(orderDataMapper).insertOrder(any());
        assertThrows(IllegalStateException.class, () ->
                orderService.placeOrder(command("AAPL", OrderSide.SELL, OrderType.MARKET, null)));

        org.mockito.Mockito.doReturn(1).when(orderDataMapper).insertOrder(any());
        assertThrows(IllegalStateException.class, () ->
                orderService.placeOrder(command("AAPL", OrderSide.SELL, OrderType.MARKET, null)));
    }

    @Test
    void cancelOrder_validatesIdAndState() {
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(null));
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder("  "));
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder("not-a-uuid"));

        UUID id = UUID.randomUUID();
        when(orderDataMapper.cancelOrderIfStatus(eq(id), eq("NEW"), eq("CANCELLED"))).thenReturn(0);
        when(orderDataMapper.findOrderById(id)).thenReturn(null);
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(id.toString()));

        Order existing = limitBuy(id);
        when(orderDataMapper.findOrderById(id)).thenReturn(existing);
        assertThrows(OrderStateConflictException.class, () -> orderService.cancelOrder(id.toString()));
    }

    @Test
    void cancelOrder_restoresBuyingPowerForLimitBuys() {
        UUID id = UUID.randomUUID();
        Order existing = limitBuy(id);
        existing.setQuantity(4);
        existing.setPrice(new BigDecimal("10"));
        Account account = activeAccount();
        account.setBuyingPower(new BigDecimal("100"));
        wireAuth(account);

        when(orderDataMapper.cancelOrderIfStatus(eq(id), eq("NEW"), eq("CANCELLED"))).thenReturn(1);
        when(orderDataMapper.findOrderById(id)).thenReturn(existing);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(1);

        OrderResponse response = orderService.cancelOrder(id.toString());

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals("Order cancelled", response.getMessage());
        verify(orderDataMapper).updateAccountBalancesWithVersion(
                eq(1001L),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("140")),
                eq(3)
        );
    }

    @Test
    void cancelOrder_leavesMarketOrdersUntouchedAndReportsConflicts() {
        UUID marketId = UUID.randomUUID();
        Order market = limitBuy(marketId);
        market.setOrderType(OrderType.MARKET);
        market.setSide(OrderSide.SELL);
        when(orderDataMapper.cancelOrderIfStatus(eq(marketId), anyString(), anyString())).thenReturn(1);
        when(orderDataMapper.findOrderById(marketId)).thenReturn(market);

        OrderResponse response = orderService.cancelOrder(marketId.toString());
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        verify(orderDataMapper, never()).updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt());

        UUID missingAfterCancel = UUID.randomUUID();
        when(orderDataMapper.cancelOrderIfStatus(eq(missingAfterCancel), anyString(), anyString())).thenReturn(1);
        when(orderDataMapper.findOrderById(missingAfterCancel)).thenReturn(null);
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(missingAfterCancel.toString()));

        UUID conflictId = UUID.randomUUID();
        Order limit = limitBuy(conflictId);
        wireAuth(activeAccount());
        when(orderDataMapper.cancelOrderIfStatus(eq(conflictId), anyString(), anyString())).thenReturn(1);
        when(orderDataMapper.findOrderById(conflictId)).thenReturn(limit);
        when(orderDataMapper.updateAccountBalancesWithVersion(anyLong(), any(), any(), anyInt())).thenReturn(0);
        assertThrows(OrderStateConflictException.class, () -> orderService.cancelOrder(conflictId.toString()));
    }

    @Test
    void getOrderHistory_validatesRangeStatusAndAccountAccess() {
        OffsetDateTime from = OffsetDateTime.parse("2026-02-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        assertThrows(InvalidOrderException.class, () -> orderService.getOrderHistory(1001L, null, from, to));

        Account account = activeAccount();
        wireAuth(account);
        when(orderDataMapper.findOrderHistory(eq(1001L), eq("NEW"), isNull(), isNull()))
                .thenReturn(List.of(new OrderHistoryEntry()));

        List<OrderHistoryEntry> rows = orderService.getOrderHistory(1001L, " new ", null, null);
        assertEquals(1, rows.size());

        when(orderDataMapper.findOrderHistory(eq(1001L), isNull(), isNull(), isNull())).thenReturn(List.of());
        assertEquals(0, orderService.getOrderHistory(1001L, "   ", null, null).size());

        assertThrows(InvalidOrderException.class, () -> orderService.getOrderHistory(1001L, "BOGUS", null, null));
        assertThrows(AccountNotFoundException.class, () -> orderService.getOrderHistory(null, null, null, null));
        assertThrows(AccountNotFoundException.class, () -> orderService.getOrderHistory(0L, null, null, null));

        account.setStatus(AccountStatus.SUSPENDED);
        assertThrows(AccountNotActiveException.class, () -> orderService.getOrderHistory(1001L, null, null, null));

        account.setStatus(AccountStatus.ACTIVE);
        when(currentUser.getAccountId()).thenReturn(null);
        assertThrows(TradingAuthenticationException.class, () -> orderService.getOrderHistory(1001L, null, null, null));
    }

    private void wireAuth(Account account) {
        when(accountDataMapper.findAccountById(account.getAccountId())).thenReturn(account);
        when(currentUser.getAccountId()).thenReturn(account.getAccountId());
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    private Account activeAccount() {
        Account account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("1000.00"));
        account.setBuyingPower(new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(3);
        return account;
    }

    private Instrument instrument(String symbol, String currency) {
        Instrument instrument = new Instrument();
        instrument.setSymbol(symbol);
        instrument.setCurrency(currency);
        instrument.setTradable(true);
        return instrument;
    }

    private PlaceOrderCommand command(String symbol, OrderSide side, OrderType type, BigDecimal price) {
        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setAccountId(1001L);
        command.setSymbol(symbol);
        command.setSide(side);
        command.setOrderType(type);
        command.setQuantity(2);
        command.setPrice(price);
        command.setIdempotencyKey("idem-1");
        return command;
    }

    private Order limitBuy(UUID id) {
        Order order = new Order();
        order.setId(id);
        order.setAccountId(1001L);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setQuantity(2);
        order.setPrice(new BigDecimal("10"));
        order.setStatus(OrderStatus.NEW);
        return order;
    }
}

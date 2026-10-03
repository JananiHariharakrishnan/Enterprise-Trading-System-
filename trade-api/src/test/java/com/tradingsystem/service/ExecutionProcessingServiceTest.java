package com.tradingsystem.service;

import com.tradingsystem.dto.request.ExecutionRequest;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.OptimisticLockException;
import com.tradingsystem.exception.MalformedExecutionException;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.messaging.TradeEventPublisher;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.service.Impl.ExecutionProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionProcessingService execution paths.
 * 
 * Covers:
 * - A settled order writes status, cash and position together
 * - A failure in any of the three leaves none of them written
 * - A second delivery affects zero rows and publishes nothing
 * - An exhausted optimistic-lock budget is reported as an error
 */
@ExtendWith(MockitoExtension.class)
class ExecutionProcessingServiceTest {

    @Mock
    private OrderDataMapper orderDataMapper;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    @InjectMocks
    private ExecutionProcessingService executionService;

    private UUID orderId;
    private UUID eventId;
    private ExecutionRequest fillRequest;
    private ExecutionRequest rejectRequest;
    private Order order;
    private Account account;
    private Position position;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        // Setup order
        order = new Order();
        order.setId(orderId);
        order.setAccountId(1001L);
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setQuantity(100);
        order.setPrice(new BigDecimal("230.00"));
        order.setStatus(OrderStatus.NEW);

        // Setup account
        account = new Account();
        account.setAccountId(1001L);
        account.setCashBalance(new BigDecimal("50000.00"));
        account.setBuyingPower(new BigDecimal("50000.00"));
        account.setVersion(5);

        // Setup position
        position = new Position();
        position.setAccountId(1001L);
        position.setSymbol("AAPL");
        position.setQuantity(50);
        position.setAverageCost(new BigDecimal("225.00"));
        position.setVersion(2);

        // Setup requests
        fillRequest = new ExecutionRequest();
        fillRequest.setStatus("FILLED");
        fillRequest.setExecutedPrice(new BigDecimal("231.50"));
        fillRequest.setEventId(eventId);

        rejectRequest = new ExecutionRequest();
        rejectRequest.setStatus("REJECTED");
        rejectRequest.setReason("PRICE_NOT_MET");
        rejectRequest.setEventId(eventId);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * Test: A settled order writes status, cash and position together.
     * 
     * Given an order with status=NEW
     * When applyExecution is called with FILLED status
     * Then order status is updated, account balance is updated, position is updated, and execution is recorded
     */
    @Test
    void settledOrderWritesStatusCashAndPositionTogether() {
        // Arrange
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "FILLED")).thenReturn(1);
        when(orderDataMapper.findOrderById(orderId)).thenReturn(order);
        when(orderDataMapper.findAccountById(1001L)).thenReturn(account);
        when(orderDataMapper.updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("26850.00"), // 50000 - (231.50 * 100)
            new BigDecimal("50000.00").add(new BigDecimal("23000.00")).subtract(new BigDecimal("23150.00")),
            5
        )).thenReturn(1);
        when(orderDataMapper.findPosition(1001L, "AAPL")).thenReturn(position);
        when(orderDataMapper.updatePositionWithVersion(
            anyLong(), anyString(), anyInt(), any(BigDecimal.class), anyInt()
        )).thenReturn(1);

        // Act
        executionService.applyExecution(orderId, fillRequest);

        // Assert
        // Verify all three state changes were attempted
        verify(orderDataMapper, times(1)).updateOrderStatusIfStatus(orderId, "NEW", "FILLED");
        verify(orderDataMapper, times(1)).updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("26850.00"),
            new BigDecimal("49850.00"),
            5
        );
        verify(orderDataMapper, times(1)).updatePositionWithVersion(
            1001L, "AAPL", 150, new BigDecimal("229.3333333333"), 2
        );
        verify(orderDataMapper, times(1)).insertExecutionWithEventId(
            orderId, 100, new BigDecimal("231.50"), eventId
        );
    }

    /**
     * Test: A failure in any of the three leaves none of them written (rollback).
     * 
     * Given an order with status=NEW
     * When position update fails (version mismatch after 3 retries)
     * Then OptimisticLockException is thrown and transaction rolls back
     */
    @Test
    void failureInPositionUpdateRollsBackTransaction() {
        // Arrange
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "FILLED")).thenReturn(1);
        when(orderDataMapper.findOrderById(orderId)).thenReturn(order);
        when(orderDataMapper.findAccountById(1001L)).thenReturn(account);
        when(orderDataMapper.updateAccountBalancesWithVersion(
            eq(1001L), any(BigDecimal.class), any(BigDecimal.class), eq(5)
        )).thenReturn(1);
        when(orderDataMapper.findPosition(1001L, "AAPL")).thenReturn(position);
        
        // Simulate version mismatch on all 3 retries
        when(orderDataMapper.updatePositionWithVersion(
            anyLong(), anyString(), anyInt(), any(BigDecimal.class), anyInt()
        )).thenReturn(0); // 0 rows affected = version mismatch

        // Act & Assert
        assertThrows(OptimisticLockException.class, () -> {
            executionService.applyExecution(orderId, fillRequest);
        });

        // Verify execution was never recorded (transaction rolled back)
        verify(orderDataMapper, never()).insertExecutionWithEventId(
            orderId, 100, new BigDecimal("231.50"), eventId
        );
    }

    /**
     * Test: A second delivery affects zero rows and publishes nothing (idempotency).
     * 
     * Given an execution with eventId already exists
     * When applyExecution is called again with same eventId
     * Then method returns silently without making any state changes
     */
    @Test
    void secondDeliveryWithSameEventIdIsIdempotent() {
        // Arrange
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(true);

        // Act
        executionService.applyExecution(orderId, fillRequest);

        // Assert
        // Verify no state changes were made
        verify(orderDataMapper, never()).updateOrderStatusIfStatus(any(UUID.class), anyString(), anyString());
        verify(orderDataMapper, never()).updateAccountBalancesWithVersion(
            anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyInt()
        );
        verify(orderDataMapper, never()).insertExecutionWithEventId(
            any(UUID.class), anyInt(), any(BigDecimal.class), any(UUID.class)
        );
        verify(tradeEventPublisher, never()).publishTradeEvent(any(TradeEventPayload.class), anyString());
    }

    /**
     * Test: Order not found throws MalformedExecutionException (permanent failure).
     * 
     * Given an order that doesn't exist in database
     * When applyExecution is called
     * Then MalformedExecutionException is thrown (to be sent to DLT)
     */
    @Test
    void orderNotFoundThrowsMalformedExecutionException() {
        // Arrange
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "FILLED")).thenReturn(1);
        when(orderDataMapper.findOrderById(orderId)).thenReturn(null);

        // Act & Assert
        assertThrows(MalformedExecutionException.class, () -> {
            executionService.applyExecution(orderId, fillRequest);
        });
    }

    /**
     * Test: Duplicate execution (already processed) throws DuplicateOrderException.
     * 
     * Given an order that was already executed (status != NEW)
     * When applyExecution is called again
     * Then DuplicateOrderException is thrown (409 Conflict)
     */
    @Test
    void duplicateExecutionThrowsDuplicateOrderException() {
        // Arrange
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "FILLED")).thenReturn(0); // 0 rows = not NEW

        // Act & Assert
        assertThrows(DuplicateOrderException.class, () -> {
            executionService.applyExecution(orderId, fillRequest);
        });
    }

    @Test
    void rejectedBuyRestoresReservedBuyingPower() {
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "REJECTED")).thenReturn(1);
        when(orderDataMapper.findOrderById(orderId)).thenReturn(order);
        when(orderDataMapper.findAccountById(1001L)).thenReturn(account);
        when(orderDataMapper.updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("50000.00"),
            new BigDecimal("73000.00"),
            5
        )).thenReturn(1);

        executionService.applyExecution(orderId, rejectRequest);

        verify(orderDataMapper).updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("50000.00"),
            new BigDecimal("73000.00"),
            5
        );
    }

    /**
     * Test: REJECTED order writes status and execution without position update.
     * 
     * Given a rejection request
     * When applyExecution is called
     * Then order status is updated to REJECTED and execution is recorded
     * And position is NOT updated
     */
    @Test
    void rejectedOrderDoesNotUpdatePosition() {
        // Arrange
        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "REJECTED")).thenReturn(1);
        when(orderDataMapper.findOrderById(orderId)).thenReturn(order);
        when(orderDataMapper.findAccountById(1001L)).thenReturn(account);
        when(orderDataMapper.updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("50000.00"),
            new BigDecimal("73000.00"),
            5
        )).thenReturn(1);

        // Act
        executionService.applyExecution(orderId, rejectRequest);

        // Assert
        verify(orderDataMapper, times(1)).updateOrderStatusIfStatus(orderId, "NEW", "REJECTED");
        verify(orderDataMapper, times(1)).insertExecutionWithEventId(orderId, 100, order.getPrice(), eventId);
        
        // Verify no position update occurs, but BUY reservation is restored.
        verify(orderDataMapper, times(1)).updateAccountBalancesWithVersion(
            1001L,
            new BigDecimal("50000.00"),
            new BigDecimal("73000.00"),
            5
        );
        verify(orderDataMapper, never()).updatePositionWithVersion(anyLong(), anyString(), anyInt(), any(), anyInt());
    }

    /**
     * Test: Missing status in request throws exception.
     */
    @Test
    void missingStatusThrowsMalformedExecutionException() {
        // Arrange
        ExecutionRequest badRequest = new ExecutionRequest();
        badRequest.setStatus(null);
        badRequest.setEventId(eventId);

        // Act & Assert
        assertThrows(MalformedExecutionException.class, () -> {
            executionService.applyExecution(orderId, badRequest);
        });
    }

    /**
     * Test: Missing executedPrice for FILLED throws exception.
     */
    @Test
    void missingExecutedPriceForFilledThrowsException() {
        // Arrange
        ExecutionRequest badRequest = new ExecutionRequest();
        badRequest.setStatus("FILLED");
        badRequest.setExecutedPrice(null); // Missing!
        badRequest.setEventId(eventId);

        when(orderDataMapper.existsExecutionByEventId(eventId)).thenReturn(false);
        when(orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", "FILLED")).thenReturn(1);
        when(orderDataMapper.findOrderById(orderId)).thenReturn(order);

        // Act & Assert
        assertThrows(MalformedExecutionException.class, () -> {
            executionService.applyExecution(orderId, badRequest);
        });
    }
}


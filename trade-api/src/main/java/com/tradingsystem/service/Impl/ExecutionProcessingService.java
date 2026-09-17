package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.request.ExecutionRequest;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.messaging.TradeEventPublisher;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.exception.OptimisticLockException;
import com.tradingsystem.exception.MalformedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service that applies execution results to the DB (guarded update -> side effects) and publishes trade-events after commit.
 */
@Service
public class ExecutionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionProcessingService.class);

    private final OrderDataMapper orderDataMapper;
    private final TradeEventPublisher tradeEventPublisher;

    public ExecutionProcessingService(
            OrderDataMapper orderDataMapper,
            TradeEventPublisher tradeEventPublisher
    ) {
        this.orderDataMapper = orderDataMapper;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 50;

    /**
     * Apply decision for an order. This method is transactional.
     * 
     * Idempotency: If eventId is provided and execution with that eventId exists,
     * returns silently (Kafka replay scenario).
     * 
     * Optimistic Locking: Position updates use version-based guards with 3 retry attempts.
     * If all 3 attempts fail, throws OptimisticLockException (permanent failure).
     * 
     * Guarded State Transition: Order status update is conditional on order being NEW.
     * If guarded update affects 0 rows, throws DuplicateOrderException.
     */
    @Transactional
    public void applyExecution(UUID orderId, ExecutionRequest request) {

        String targetStatus = request.getStatus();
        if (targetStatus == null) {
            throw new MalformedExecutionException("status is required");
        }

        // STEP 0: Check idempotency by event_id (Kafka replay detection)
        // If caller provides eventId and execution with that eventId exists, return silently
        UUID eventId = request.getEventId();
        if (eventId != null && orderDataMapper.existsExecutionByEventId(eventId)) {
            log.info("Execution with event_id {} already exists (idempotent replay)", eventId);
            return;
        }

        // STEP 1: Guarded update: TRY change order status from NEW -> targetStatus
        int affected = orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", targetStatus);
        if (affected != 1) {
            log.info("Guarded update for order {} failed (already processed or not NEW)", orderId);
            throw new DuplicateOrderException();
        }

        // STEP 2: Load the order (now status = targetStatus)
        Order order = orderDataMapper.findOrderById(orderId);
        if (order == null) {
            throw new MalformedExecutionException("Order not found: " + orderId);
        }

        // For REJECTED: we will publish a REJECTED event after commit and return
        if ("REJECTED".equalsIgnoreCase(targetStatus)) {

            TradeEventPayload payload = new TradeEventPayload();
            payload.setOrderId(orderId.toString());
            payload.setAccountId(order.getAccountId());
            payload.setSymbol(order.getSymbol());
            payload.setSide(order.getSide() == null ? null : order.getSide().name());
            payload.setQuantity(BigDecimal.valueOf(order.getQuantity()));
            payload.setPrice(order.getPrice());
            payload.setExecutedPrice(null);
            payload.setStatus("REJECTED");
            payload.setReason(request.getReason());
            payload.setCashDelta(null);
            payload.setPositionQuantityAfter(null);
            payload.setAverageCostAfter(null);
            payload.setExecutedOn(java.time.OffsetDateTime.now().toString());

            // Record the execution (rejection) with eventId for idempotency
            if (eventId != null) {
                orderDataMapper.insertExecutionWithEventId(orderId, order.getQuantity(), order.getPrice(), eventId);
            } else {
                orderDataMapper.insertExecution(orderId, order.getQuantity(), order.getPrice());
            }
            
            // publish after commit
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tradeEventPublisher.publishTradeEvent(payload, "ORDER_REJECTED");
                }
            });

            return;
        }

        // If FILLED -> perform balance updates, position updates, insert execution row
        if ("FILLED".equalsIgnoreCase(targetStatus)) {

            BigDecimal executedPrice = request.getExecutedPrice();
            if (executedPrice == null) {
                throw new MalformedExecutionException("executedPrice is required for FILLED");
            }

            // 3) Account update: optimistic version check
            Account account = orderDataMapper.findAccountById(order.getAccountId());
            if (account == null) {
                throw new MalformedExecutionException("Account not found: " + order.getAccountId());
            }

            BigDecimal orderValue = executedPrice.multiply(BigDecimal.valueOf((long) order.getQuantity()));

            BigDecimal newCashBalance = account.getCashBalance();
            BigDecimal newBuyingPower = account.getBuyingPower();

            if ("BUY".equalsIgnoreCase(order.getSide().name())) {
                newCashBalance = newCashBalance.subtract(orderValue);
                newBuyingPower = newBuyingPower.subtract(orderValue);
            } else if ("SELL".equalsIgnoreCase(order.getSide().name())) {
                newCashBalance = newCashBalance.add(orderValue);
                newBuyingPower = newBuyingPower.add(orderValue);
            }

            int accountRows = orderDataMapper.updateAccountBalancesWithVersion(
                    account.getAccountId(),
                    newCashBalance,
                    newBuyingPower,
                    account.getVersion()
            );

            if (accountRows != 1) {
                log.error("Account optimistic update failed for account {} (version mismatch)", account.getAccountId());
                throw new OrderStateConflictException();
            }

            // 4) Position update (insert or update) with RETRY LOGIC
            Position position = orderDataMapper.findPosition(order.getAccountId(), order.getSymbol());
            int newQuantity;
            java.math.BigDecimal newAverageCost = null;

            if ("BUY".equalsIgnoreCase(order.getSide().name())) {
                if (position == null) {
                    // insert a new position with version 0
                    orderDataMapper.insertPosition(order.getAccountId(), order.getSymbol(), order.getQuantity(), executedPrice);
                    newQuantity = order.getQuantity();
                    newAverageCost = executedPrice;
                } else {
                    // UPDATE with retry logic for optimistic locking
                    newQuantity = position.getQuantity() + order.getQuantity();
                    java.math.BigDecimal previousCost = position.getAverageCost().multiply(java.math.BigDecimal.valueOf(position.getQuantity()));
                    java.math.BigDecimal orderCost = executedPrice.multiply(java.math.BigDecimal.valueOf(order.getQuantity()));
                    newAverageCost = previousCost.add(orderCost)
                            .divide(java.math.BigDecimal.valueOf(newQuantity), 10, java.math.RoundingMode.HALF_UP);

                    updatePositionWithRetry(order.getAccountId(), order.getSymbol(), newQuantity, newAverageCost, position.getVersion());
                }
            } else {
                // SELL: decrement existing position
                if (position == null) {
                    throw new MalformedExecutionException("Cannot sell without existing position");
                }
                int existingQty = position.getQuantity();
                if (existingQty < order.getQuantity()) {
                    throw new MalformedExecutionException("Insufficient holdings for SELL");
                }
                newQuantity = existingQty - order.getQuantity();
                newAverageCost = newQuantity == 0 ? java.math.BigDecimal.ZERO : position.getAverageCost();
                
                updatePositionWithRetry(order.getAccountId(), order.getSymbol(), newQuantity, newAverageCost, position.getVersion());
            }

            // 5) Insert execution row with eventId for idempotency
            if (eventId != null) {
                orderDataMapper.insertExecutionWithEventId(orderId, order.getQuantity(), executedPrice, eventId);
            } else {
                orderDataMapper.insertExecution(orderId, order.getQuantity(), executedPrice);
            }

            // 6) Build trade-event payload and publish after commit
            TradeEventPayload payload = new TradeEventPayload();
            payload.setOrderId(orderId.toString());
            payload.setAccountId(order.getAccountId());
            payload.setSymbol(order.getSymbol());
            payload.setSide(order.getSide() == null ? null : order.getSide().name());
            payload.setQuantity(java.math.BigDecimal.valueOf(order.getQuantity()));
            payload.setPrice(order.getPrice());
            payload.setExecutedPrice(executedPrice);
            payload.setStatus("FILLED");
            payload.setReason(null);

            // cashDelta: negative for buys, positive for sells. Use scale matching contract
            java.math.BigDecimal cashDelta = orderValue.negate();
            if ("SELL".equalsIgnoreCase(order.getSide().name())) {
                cashDelta = orderValue;
            }
            payload.setCashDelta(cashDelta);
            payload.setPositionQuantityAfter(newQuantity);
            payload.setAverageCostAfter(newAverageCost);
            payload.setExecutedOn(java.time.OffsetDateTime.now().toString());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tradeEventPublisher.publishTradeEvent(payload, "ORDER_FILLED");
                }
            });

            return;
        }

        // Unknown status
        throw new MalformedExecutionException("Unknown status: " + targetStatus);
    }

    /**
     * Update position with optimistic locking and retry logic.
     * 
     * Retries up to MAX_RETRY_ATTEMPTS times with exponential backoff.
     * On each retry, re-reads the position version and attempts update again.
     * 
     * If all retries fail: throws OptimisticLockException (permanent failure, to DLT).
     */
    private void updatePositionWithRetry(Long accountId, String symbol, int quantity, 
                                         java.math.BigDecimal averageCost, int initialVersion) {
        int currentVersion = initialVersion;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                int updated = orderDataMapper.updatePositionWithVersion(
                        accountId, symbol, quantity, averageCost, currentVersion
                );
                
                if (updated == 1) {
                    // Success!
                    log.info("Position update succeeded for account={} symbol={} on attempt {}", 
                        accountId, symbol, attempt);
                    return;
                }
                
                // Version mismatch - concurrent update detected
                log.warn("Position update failed due to version mismatch (concurrent update), attempt {}/{}", 
                    attempt, MAX_RETRY_ATTEMPTS);
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    // Wait before retrying (exponential backoff)
                    long backoffMs = BACKOFF_BASE_MS * attempt; // 50ms, 100ms, ...
                    log.info("Retrying position update in {}ms...", backoffMs);
                    Thread.sleep(backoffMs);
                    
                    // Re-read position to get updated version
                    Position latestPosition = orderDataMapper.findPosition(accountId, symbol);
                    if (latestPosition != null) {
                        currentVersion = latestPosition.getVersion();
                        log.info("Re-read position version: {}", currentVersion);
                    } else {
                        throw new MalformedExecutionException("Position disappeared during retry for account=" + 
                            accountId + " symbol=" + symbol);
                    }
                }
                
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new OptimisticLockException("Position update interrupted: " + ie.getMessage(), ie);
            }
        }
        
        // All retries exhausted
        throw new OptimisticLockException(
            "Failed to update position after " + MAX_RETRY_ATTEMPTS + " retries for account=" + 
            accountId + " symbol=" + symbol + ". Concurrent updates may be blocking this transaction."
        );
    }
}
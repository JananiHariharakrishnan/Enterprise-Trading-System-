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

    /**
     * Apply decision for an order. This method is transactional.
     * If the order was already processed (guarded update affects 0 rows) we throw DuplicateOrderException.
     */
    @Transactional
    public void applyExecution(UUID orderId, ExecutionRequest request) {

        String targetStatus = request.getStatus();
        if (targetStatus == null) {
            throw new IllegalArgumentException("status is required");
        }

        // 1) Guarded update: TRY change order status from NEW -> targetStatus
        int affected = orderDataMapper.updateOrderStatusIfStatus(orderId, "NEW", targetStatus);
        if (affected != 1) {
            log.info("Guarded update for order {} failed (already processed or not NEW)", orderId);
            throw new DuplicateOrderException();
        }

        // 2) Load the order (now status = targetStatus)
        Order order = orderDataMapper.findOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
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
                throw new IllegalArgumentException("executedPrice is required for FILLED");
            }

            // 3) Account update: optimistic version check
            Account account = orderDataMapper.findAccountById(order.getAccountId());
            if (account == null) {
                throw new IllegalArgumentException("Account not found: " + order.getAccountId());
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

            // 4) Position update (insert or update)
            Position position = orderDataMapper.findPosition(order.getAccountId(), order.getSymbol());
            int newQuantity;
            java.math.BigDecimal newAverageCost = null;

            if ("BUY".equalsIgnoreCase(order.getSide().name())) {
                if (position == null) {
                    // insert a new position
                    orderDataMapper.insertPosition(order.getAccountId(), order.getSymbol(), order.getQuantity(), executedPrice);
                    newQuantity = order.getQuantity();
                    newAverageCost = executedPrice;
                } else {
                    int existingQty = position.getQuantity();
                    int existingQuantity = existingQty;
                    newQuantity = existingQuantity + order.getQuantity();
                    java.math.BigDecimal previousCost = position.getAverageCost().multiply(java.math.BigDecimal.valueOf(existingQuantity));
                    java.math.BigDecimal orderCost = executedPrice.multiply(java.math.BigDecimal.valueOf(order.getQuantity()));
                    newAverageCost = previousCost.add(orderCost)
                            .divide(java.math.BigDecimal.valueOf(newQuantity), 10, java.math.RoundingMode.HALF_UP);

                    int updated = orderDataMapper.updatePosition(order.getAccountId(), order.getSymbol(), newQuantity, newAverageCost);
                    if (updated != 1) {
                        throw new OrderStateConflictException();
                    }
                }
            } else {
                // SELL: decrement existing position
                if (position == null) {
                    throw new IllegalArgumentException("Cannot sell without existing position");
                }
                int existingQty = position.getQuantity();
                if (existingQty < order.getQuantity()) {
                    throw new IllegalArgumentException("Insufficient holdings for SELL");
                }
                newQuantity = existingQty - order.getQuantity();
                newAverageCost = newQuantity == 0 ? java.math.BigDecimal.ZERO : position.getAverageCost();
                int updated = orderDataMapper.updatePosition(order.getAccountId(), order.getSymbol(), newQuantity, newAverageCost);
                if (updated != 1) {
                    throw new OrderStateConflictException();
                }
            }

            // 5) Insert execution row
            orderDataMapper.insertExecution(orderId, order.getQuantity(), executedPrice);

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
        throw new IllegalArgumentException("Unknown status: " + targetStatus);
    }
}
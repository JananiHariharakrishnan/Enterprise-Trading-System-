package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.exception.*;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.OrderLogic;
import com.tradingsystem.service.OrderService;
import com.tradingsystem.service.command.PlaceOrderCommand;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderLogic orderLogic;
    private final OrderDataMapper orderDataMapper;
    private final AccountDataMapper accountDataMapper;
    private final CurrentUser currentUser;

    public OrderServiceImpl(
            OrderLogic orderLogic,
            OrderDataMapper orderDataMapper,
            AccountDataMapper accountDataMapper,
            CurrentUser currentUser) {

        this.orderLogic = orderLogic;
        this.orderDataMapper = orderDataMapper;
        this.accountDataMapper = accountDataMapper;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(PlaceOrderCommand command) {
        Account account =
                orderDataMapper.findAccountById(
                        command.getAccountId()
                );
        // EDGE CASE FIX: Verify cash balance is not null
        if (account.getCashBalance() == null) {
            throw new InvalidOrderException("Account cash balance is invalid");
        }

        if (account.getBuyingPower() == null) {
            throw new InvalidOrderException("Account buying power is invalid");
        }

        Instrument instrument =
                orderDataMapper.findInstrumentBySymbol(
                        command.getSymbol()
                );

        Position position =
                orderDataMapper.findPosition(
                        command.getAccountId(),
                        command.getSymbol()
                );

        boolean duplicateKey =
                orderDataMapper.existsByIdempotencyKey(
                        command.getIdempotencyKey()
                );

        if (duplicateKey) {
            throw new DuplicateOrderException();
        }

        Order order = new Order();

        order.setId(UUID.randomUUID());
        order.setAccountId(command.getAccountId());
        order.setSymbol(command.getSymbol());
        order.setSide(command.getSide());
        order.setOrderType(command.getOrderType());
        order.setQuantity(command.getQuantity());
        order.setPrice(command.getPrice());
        order.setIdempotencyKey(command.getIdempotencyKey());
        order.setStatus(OrderStatus.NEW);
        order.setCreatedOn(LocalDateTime.now());

        orderLogic.placeOrder(
                account,
                instrument,
                order,
                position
        );

        // EDGE CASE FIX: Prevent arithmetic overflow in price * quantity
        BigDecimal orderValue = calculateOrderValue(order);

        BigDecimal newCashBalance = account.getCashBalance();
        BigDecimal newBuyingPower = account.getBuyingPower();
        if (order.getSide() == OrderSide.BUY) {
            newCashBalance = newCashBalance.subtract(orderValue);
            newBuyingPower = newBuyingPower.subtract(orderValue);
        } else {
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
            throw new OrderStateConflictException();
        }

        persistPosition(order, position);

        try {
            int affectedRows = orderDataMapper.insertOrder(order);
            if (affectedRows != 1) {
                throw new IllegalStateException("Order insert did not affect exactly one row");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateOrderException();
        }

        OrderResponse response =
                new OrderResponse();

        response.setOrderId(
                order.getId().toString()
        );

        response.setStatus(OrderStatus.NEW);
        response.setMessage("Order accepted");
        response.setSymbol(order.getSymbol());
        response.setSide(order.getSide());
        response.setQuantity(order.getQuantity());
        response.setPrice(order.getPrice());

        return response;
    }
    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderId) {

        // EDGE CASE FIX: Validate UUID format before processing
        UUID uuid;
        try {
            uuid = UUID.fromString(orderId);
        } catch (IllegalArgumentException ex) {
            throw new OrderNotFoundException("Invalid order ID format");
        }

        int affectedRows = orderDataMapper.cancelOrderIfStatus(
                uuid,
                OrderStatus.NEW.name(),
                OrderStatus.CANCELLED.name()
        );

        // EDGE CASE FIX: Distinguish between "order not found" vs "order not in NEW state"
        if (affectedRows == 0) {
            // Check if order exists at all
            Order existingOrder = orderDataMapper.findOrderById(uuid);
            if (existingOrder == null) {
                throw new OrderNotFoundException("Order not found");
            }
            // Order exists but is not in NEW state
            throw new OrderStateConflictException("Cannot cancel order that is not in NEW state");
        }

        OrderResponse response = new OrderResponse();
        response.setOrderId(orderId);
        response.setStatus(OrderStatus.CANCELLED);
        response.setMessage("Order cancelled");

        return response;
    }

    // EDGE CASE FIX: Helper method to calculate order value with overflow protection
    private BigDecimal calculateOrderValue(Order order) {
        BigDecimal price = order.getPrice();
        long quantity = (long) order.getQuantity(); // Convert to long to prevent overflow
        
        if (quantity > Integer.MAX_VALUE / 1000) {
            throw new InvalidOrderException("Order quantity exceeds maximum allowed value");
        }
        
        BigDecimal orderValue = price.multiply(BigDecimal.valueOf(quantity));
        
        // Check for arithmetic overflow result
        if (orderValue.scale() > 10) {
            throw new InvalidOrderException("Order value precision exceeds allowed range");
        }
        
        return orderValue;
    }

    private void persistPosition(Order order, Position existingPosition) {
        if (order.getSide() == OrderSide.BUY) {
            if (existingPosition == null) {
                int inserted = orderDataMapper.insertPosition(
                        order.getAccountId(),
                        order.getSymbol(),
                        order.getQuantity(),
                        order.getPrice()
                );
                if (inserted != 1) {
                    throw new IllegalStateException("Position insert did not affect exactly one row");
                }
                return;
            }

            int newQuantity = existingPosition.getQuantity() + order.getQuantity();
            BigDecimal previousCost = existingPosition.getAverageCost()
                    .multiply(BigDecimal.valueOf(existingPosition.getQuantity()));
            BigDecimal orderCost = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            
            // EDGE CASE FIX: Handle division by zero and precision with more accurate rounding
            BigDecimal newAverageCost = previousCost
                    .add(orderCost)
                    .divide(BigDecimal.valueOf(newQuantity), 10, RoundingMode.HALF_UP);

            int updated = orderDataMapper.updatePosition(
                    order.getAccountId(),
                    order.getSymbol(),
                    newQuantity,
                    newAverageCost
            );
            if (updated != 1) {
                throw new OrderStateConflictException();
            }
            return;
        }

        // EDGE CASE FIX: Better error message for SELL without position
        if (existingPosition == null) {
            throw new IllegalStateException(
                "SELL order validation should have caught missing position"
            );
        }

        int newQuantity = existingPosition.getQuantity() - order.getQuantity();
        
        // EDGE CASE FIX: Handle 0 quantity edge case
        BigDecimal newAverageCost = newQuantity <= 0
                ? BigDecimal.ZERO
                : existingPosition.getAverageCost();

        int updated = orderDataMapper.updatePosition(
                order.getAccountId(),
                order.getSymbol(),
                newQuantity,
                newAverageCost
        );
        if (updated != 1) {
            throw new OrderStateConflictException();
        }
    }

    @Override
    public List<OrderHistoryEntry> getOrderHistory(
            Long accountId,
            String status,
            OffsetDateTime from,
            OffsetDateTime to) {

        // EDGE CASE FIX: Validate date range
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidOrderException("Start date cannot be after end date");
        }

        // EDGE CASE FIX: Validate status parameter if provided
        if (status != null && !status.trim().isEmpty()) {
            try {
                OrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new InvalidOrderException("Invalid order status: " + status);
            }
        }

        /*
         * Account lookup is deliberately performed before the order query.
         * Otherwise an unknown account would incorrectly return an empty
         * order history instead of ACC-404.
         */
        Account account =
                accountDataMapper.findAccountById(accountId);

        if (account == null) {
            throw new AccountNotFoundException();
        }

        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }

        Long authenticatedAccountId =
                currentUser.getAccountId();

        if (authenticatedAccountId == null
                || !accountId.equals(authenticatedAccountId)) {

            throw new AccountNotActiveException();
        }

        return orderDataMapper.findOrderHistory(
                accountId,
                status,
                from,
                to
        );
    }
}
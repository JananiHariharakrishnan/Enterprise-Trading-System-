package com.tradingsystem.service.Impl;

import java.time.ZoneOffset;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
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
import com.tradingsystem.messaging.OrderPlacedEvent;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int MAX_ORDER_QUANTITY = 1_000_000;
    private static final int AVERAGE_COST_SCALE = 10;

    private final OrderLogic orderLogic;
    private final OrderDataMapper orderDataMapper;
    private final AccountDataMapper accountDataMapper;
    private final CurrentUser currentUser;
    private final OrderEventPublisher orderEventPublisher;

    public OrderServiceImpl(
            OrderLogic orderLogic,
            OrderDataMapper orderDataMapper,
            AccountDataMapper accountDataMapper,
            CurrentUser currentUser,
            OrderEventPublisher orderEventPublisher
    ) {
        this.orderLogic = orderLogic;
        this.orderDataMapper = orderDataMapper;
        this.accountDataMapper = accountDataMapper;
        this.currentUser = currentUser;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(PlaceOrderCommand command) {

        if (command == null) {
            throw new InvalidOrderException("Order request cannot be null");
        }

        Account account = getAuthorizedAccount(command.getAccountId());

        if (account.getCashBalance() == null) {
            throw new InvalidOrderException(
                    "Account cash balance is invalid"
            );
        }

        if (account.getBuyingPower() == null) {
            throw new InvalidOrderException(
                    "Account buying power is invalid"
            );
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
        order.setPrice(command.getPrice()); // may be null for MARKET orders
        order.setIdempotencyKey(command.getIdempotencyKey());
        order.setStatus(OrderStatus.NEW);
        order.setCreatedOn(LocalDateTime.now());

        /*
         * OrderLogic validates:
         * - account status
         * - instrument availability
         * - quantity
         * - price (for LIMIT orders)
         * - available funds (for LIMIT BUY orders)
         * - available holdings (for SELL)
         */
        orderLogic.placeOrder(
                account,
                instrument,
                order,
                position
        );

        // If a price is present (LIMIT path or explicitly provided), perform synchronous
        // balance and position updates. If price is null (MARKET), skip synchronous fill
        // and let the executor price/execute later.
        boolean pricePresent = order.getPrice() != null;

        if (pricePresent) {

            BigDecimal orderValue = calculateOrderValue(order);

            BigDecimal newCashBalance = account.getCashBalance();
            BigDecimal newBuyingPower = account.getBuyingPower();

            if (order.getSide() == OrderSide.BUY) {
                newCashBalance = newCashBalance.subtract(orderValue);
                newBuyingPower = newBuyingPower.subtract(orderValue);
            } else if (order.getSide() == OrderSide.SELL) {
                newCashBalance = newCashBalance.add(orderValue);
                newBuyingPower = newBuyingPower.add(orderValue);
            } else {
                throw new InvalidOrderException("Invalid order side");
            }

            int accountRows =
                    orderDataMapper.updateAccountBalancesWithVersion(
                            account.getAccountId(),
                            newCashBalance,
                            newBuyingPower,
                            account.getVersion()
                    );

            if (accountRows != 1) {
                throw new OrderStateConflictException();
            }

            persistPosition(order, position);
        }

        try {
            int affectedRows = orderDataMapper.insertOrder(order);

            if (affectedRows != 1) {
                throw new IllegalStateException(
                        "Order insert did not affect exactly one row"
                );
            }

        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateOrderException();
        }

        /*
         * Kafka publishing occurs only after successful database commit.
         */
        publishAfterCommit(order);

        OrderResponse response = new OrderResponse();

        response.setOrderId(order.getId().toString());
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

        if (orderId == null || orderId.isBlank()) {
            throw new OrderNotFoundException("Order ID is required");
        }

        UUID uuid;

        try {
            uuid = UUID.fromString(orderId);
        } catch (IllegalArgumentException exception) {
            throw new OrderNotFoundException(
                    "Invalid order ID format"
            );
        }

        int affectedRows =
                orderDataMapper.cancelOrderIfStatus(
                        uuid,
                        OrderStatus.NEW.name(),
                        OrderStatus.CANCELLED.name()
                );

        if (affectedRows == 0) {

            Order existingOrder =
                    orderDataMapper.findOrderById(uuid);

            if (existingOrder == null) {
                throw new OrderNotFoundException("Order not found");
            }

            throw new OrderStateConflictException(
                    "Cannot cancel order that is not in NEW state"
            );
        }

        OrderResponse response = new OrderResponse();

        response.setOrderId(orderId);
        response.setStatus(OrderStatus.CANCELLED);
        response.setMessage("Order cancelled");

        return response;
    }
    private void publishAfterCommit(Order order) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Order event cannot be registered outside a transaction"
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        // Build an OrderPlacedEvent from the Order model and publish it
                        OffsetDateTime createdOn = order.getCreatedOn() == null
                                ? OffsetDateTime.now()
                                : order.getCreatedOn().atOffset(ZoneOffset.UTC);

                        OrderPlacedEvent event = new OrderPlacedEvent(
                                order.getId(),
                                order.getAccountId(),
                                order.getSymbol(),
                                order.getSide() != null ? order.getSide().name() : null,
                                order.getOrderType() != null ? order.getOrderType().name() : null,
                                BigDecimal.valueOf(order.getQuantity()),
                                order.getPrice(),
                                order.getStatus() != null ? order.getStatus().name() : null,
                                order.getIdempotencyKey(),
                                createdOn
                        );

                        orderEventPublisher.publishOrderPlaced(event);
                    }
                }
        );
    }
    private BigDecimal calculateOrderValue(Order order) {

        if (order == null) {
            throw new InvalidOrderException("Order cannot be null");
        }

        if (order.getPrice() == null) {
            throw new InvalidOrderException(
                    "Order price cannot be null"
            );
        }

        if (order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException(
                    "Order price must be greater than zero"
            );
        }

        if (order.getQuantity() <= 0) {
            throw new InvalidOrderException(
                    "Order quantity must be greater than zero"
            );
        }

        if (order.getQuantity() > MAX_ORDER_QUANTITY) {
            throw new InvalidOrderException(
                    "Order quantity exceeds maximum allowed value"
            );
        }

        return order.getPrice()
                .multiply(BigDecimal.valueOf(order.getQuantity()));
    }

    private void persistPosition(
            Order order,
            Position existingPosition
    ) {

        if (order.getSide() == OrderSide.BUY) {

            if (existingPosition == null) {

                int inserted =
                        orderDataMapper.insertPosition(
                                order.getAccountId(),
                                order.getSymbol(),
                                order.getQuantity(),
                                order.getPrice()
                        );

                if (inserted != 1) {
                    throw new IllegalStateException(
                            "Position insert did not affect exactly one row"
                    );
                }

                return;
            }

            int existingQuantity = existingPosition.getQuantity();

            if (existingQuantity < 0) {
                throw new OrderStateConflictException(
                        "Existing position quantity is invalid"
                );
            }

            int newQuantity =
                    existingQuantity + order.getQuantity();

            BigDecimal averageCost =
                    existingPosition.getAverageCost();

            if (averageCost == null) {
                throw new OrderStateConflictException(
                        "Existing position average cost is invalid"
                );
            }

            BigDecimal previousCost =
                    averageCost.multiply(
                            BigDecimal.valueOf(existingQuantity)
                    );

            BigDecimal orderCost =
                    order.getPrice().multiply(
                            BigDecimal.valueOf(order.getQuantity())
                    );

            BigDecimal newAverageCost =
                    previousCost
                            .add(orderCost)
                            .divide(
                                    BigDecimal.valueOf(newQuantity),
                                    AVERAGE_COST_SCALE,
                                    RoundingMode.HALF_UP
                            );

            int updated =
                    orderDataMapper.updatePosition(
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

        if (order.getSide() != OrderSide.SELL) {
            throw new InvalidOrderException("Invalid order side");
        }

        if (existingPosition == null) {
            throw new InvalidOrderException(
                    "Cannot sell without an existing position"
            );
        }

        int existingQuantity = existingPosition.getQuantity();

        if (existingQuantity < order.getQuantity()) {
            throw new InvalidOrderException(
                    "Insufficient holdings for SELL order"
            );
        }

        int newQuantity =
                existingQuantity - order.getQuantity();

        BigDecimal newAverageCost =
                newQuantity == 0
                        ? BigDecimal.ZERO
                        : existingPosition.getAverageCost();

        int updated =
                orderDataMapper.updatePosition(
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
            OffsetDateTime to
    ) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidOrderException(
                    "Start date cannot be after end date"
            );
        }

        String normalizedStatus = null;

        if (status != null && !status.trim().isEmpty()) {

            normalizedStatus = status.trim().toUpperCase();

            try {
                OrderStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException exception) {
                throw new InvalidOrderException(
                        "Invalid order status: " + status
                );
            }
        }

        getAuthorizedAccount(accountId);

        return orderDataMapper.findOrderHistory(
                accountId,
                normalizedStatus,
                from,
                to
        );
    }

    private Account getAuthorizedAccount(Long accountId) {

        if (accountId == null || accountId <= 0) {
            throw new AccountNotFoundException();
        }

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

        if (authenticatedAccountId == null) {
            throw new TradingAuthenticationException();
        }

        if (!accountId.equals(authenticatedAccountId)) {
            throw new TradingAuthenticationException();
        }

        return account;
    }
}
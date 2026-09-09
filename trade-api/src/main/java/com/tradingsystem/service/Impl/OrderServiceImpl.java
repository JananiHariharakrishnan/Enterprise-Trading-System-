package com.tradingsystem.service.Impl;

import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.service.OrderLogic;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.exception.DuplicateOrderException;
import com.tradingsystem.exception.OrderStateConflictException;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
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
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderLogic orderLogic;
    private final OrderDataMapper orderDataMapper;

    public OrderServiceImpl(OrderLogic orderLogic, OrderDataMapper orderDataMapper) {
        this.orderLogic = orderLogic;
        this.orderDataMapper = orderDataMapper;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(PlaceOrderCommand command) {
        Account account = orderDataMapper.findAccountById(command.getAccountId());
        Instrument instrument = orderDataMapper.findInstrumentBySymbol(command.getSymbol());
        Position position = orderDataMapper.findPosition(command.getAccountId(), command.getSymbol());

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

        BigDecimal orderValue = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));

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
        int affectedRows = orderDataMapper.cancelOrderIfStatus(
                UUID.fromString(orderId),
                OrderStatus.NEW.name(),
                OrderStatus.CANCELLED.name()
        );
        if (affectedRows != 1) {
            throw new OrderStateConflictException();
        }

        OrderResponse response = new OrderResponse();

        response.setOrderId(orderId);

        response.setStatus(OrderStatus.CANCELLED);

        response.setMessage("Order cancelled");

        return response;
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
            BigDecimal newAverageCost = previousCost
                    .add(orderCost)
                    .divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);

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

        int newQuantity = Objects.requireNonNull(existingPosition).getQuantity() - order.getQuantity();
        BigDecimal newAverageCost = newQuantity == 0
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
            OffsetDateTime to
    ) {
        return orderDataMapper.findOrderHistory(accountId, status, from, to);
    }
}
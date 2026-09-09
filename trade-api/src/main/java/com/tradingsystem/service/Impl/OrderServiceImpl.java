package com.tradingsystem.service.Impl;

import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.service.OrderLogic;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.service.OrderService;
import com.tradingsystem.service.command.PlaceOrderCommand;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
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
    public OrderResponse placeOrder(PlaceOrderCommand command) {
        Account account = orderDataMapper.findAccountById(command.getAccountId());
        Instrument instrument = orderDataMapper.findInstrumentBySymbol(command.getSymbol());
        Position position = orderDataMapper.findPosition(command.getAccountId(), command.getSymbol());
        boolean duplicateKey = orderDataMapper.existsByIdempotencyKey(command.getIdempotencyKey());

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
                position,
                duplicateKey
        );

        int affectedRows = orderDataMapper.insertOrder(order);
        if (affectedRows != 1) {
            throw new IllegalStateException("Order insert did not affect exactly one row");
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
    public OrderResponse cancelOrder(String orderId) {

        OrderResponse response = new OrderResponse();

        response.setOrderId(orderId);

        response.setStatus(OrderStatus.CANCELLED);

        response.setMessage("Order cancelled");

        return response;
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
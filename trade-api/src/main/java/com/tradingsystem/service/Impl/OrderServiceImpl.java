package com.tradingsystem.service.Impl;

import com.tradingsystem.service.OrderLogic;
import com.tradingsystem.dto.request.PlaceOrderRequest;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.Order;
import com.tradingsystem.model.Position;
import com.tradingsystem.service.OrderService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderLogic orderLogic;

    public OrderServiceImpl(OrderLogic orderLogic) {
        this.orderLogic = orderLogic;
    }

    @Override
    public OrderResponse placeOrder(PlaceOrderRequest request) {

        /*
         * Temporary objects.
         * Later these will come from repositories/database.
         */

        Account account = new Account();

        // Temporary account setup
        account.setCashBalance(
                request.getPrice()
                        .multiply(
                                BigDecimal.valueOf(
                                        request.getQuantity()
                                )
                        )
        );


        Instrument instrument = new Instrument();

        instrument.setTradable(true);


        Order order = new Order();

        order.setId(UUID.randomUUID());

        order.setAccountId(request.getAccountId());

        order.setSymbol(request.getSymbol());

        order.setSide(request.getSide());

        order.setQuantity(request.getQuantity());

        order.setPrice(request.getPrice());

        order.setStatus(OrderStatus.NEW);

        order.setCreatedOn(LocalDateTime.now());


        Position position = new Position();


        // Apply trading rules
        orderLogic.placeOrder(
                account,
                instrument,
                order,
                position
        );


        // Create response
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

        return Collections.emptyList();
    }
}
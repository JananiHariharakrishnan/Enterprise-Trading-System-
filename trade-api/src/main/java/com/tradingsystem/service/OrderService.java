
package com.tradingsystem.service;

import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;
import com.tradingsystem.service.command.PlaceOrderCommand;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderService {

    OrderResponse placeOrder(PlaceOrderCommand command);

    OrderResponse cancelOrder(String orderId);

    List<OrderHistoryEntry> getOrderHistory(
            Long accountId,
            String status,
            OffsetDateTime from,
            OffsetDateTime to
    );
}


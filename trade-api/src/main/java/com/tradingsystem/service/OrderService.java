
package com.tradingsystem.service;

import com.tradingsystem.dto.request.PlaceOrderRequest;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.OrderResponse;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderService {

    OrderResponse placeOrder(PlaceOrderRequest request);

    OrderResponse cancelOrder(String orderId);

    List<OrderHistoryEntry> getOrderHistory(
            Long accountId,
            String status,
            OffsetDateTime from,
            OffsetDateTime to
    );
}


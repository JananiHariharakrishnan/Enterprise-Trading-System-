package com.tradingsystem.domain.DTO;

import com.tradingsystem.domain.enums.OrderSide;

import java.math.BigDecimal;

public class PlaceOrderRequest {

    private Long accountId;
    private String symbol;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal price;
    private String idempotencyKey;
}

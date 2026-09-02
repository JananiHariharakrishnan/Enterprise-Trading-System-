package com.tradingsystem.domain.DTO.request;

import com.tradingsystem.domain.enums.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class PlaceOrderRequest {

    @NotNull
    @Positive
    private Long accountId;

    @NotBlank
    @Size(min = 1, max = 20)
    private String symbol;

    @NotNull
    private OrderSide side;

    @NotNull
    @Positive
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal price;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
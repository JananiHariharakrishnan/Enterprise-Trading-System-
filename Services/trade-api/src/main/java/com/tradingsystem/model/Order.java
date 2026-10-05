package com.tradingsystem.model;

import com.tradingsystem.enums.OrderSide;
import com.tradingsystem.enums.OrderStatus;
import com.tradingsystem.enums.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Order {

    private UUID id;
    private Long accountId;
    private String symbol;
    private OrderSide side;
    private OrderType orderType;
    private int quantity;
    private BigDecimal price;
    private OrderStatus status;
    private String idempotencyKey;
    private LocalDateTime createdOn;
    
    // Temporary field for executor price (not persisted)
    private transient BigDecimal executorPrice;

    // Currency conversion tracking (for frontend display)
    private String originalCurrency;  // e.g., "INR" if conversion occurred
    private BigDecimal originalPrice; // Price before USD conversion

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public String getOriginalCurrency() {
        return originalCurrency;
    }

    public void setOriginalCurrency(String originalCurrency) {
        this.originalCurrency = originalCurrency;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getExecutorPrice() {
        return executorPrice;
    }

    public void setExecutorPrice(BigDecimal executorPrice) {
        this.executorPrice = executorPrice;
    }

    public void validateOrderValue() throws ArithmeticException {
        // EDGE CASE: Validate price * quantity doesn't overflow
        if (this.price == null || this.quantity <= 0) {
            throw new IllegalArgumentException("Invalid order parameters");
        }
        
        BigDecimal orderValue = this.price
                .multiply(BigDecimal.valueOf(this.quantity));
        
        if (orderValue.scale() > 10) {
            throw new ArithmeticException("Order value precision exceeds limit");
        }
    }
}
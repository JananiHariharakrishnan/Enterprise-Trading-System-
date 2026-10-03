package com.tradingsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Execution{

    private Long id;
    private UUID orderId;
    private int quantity;
    private BigDecimal price;
    private LocalDateTime executedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
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

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public BigDecimal totalValue() {
        if (this.price == null) return BigDecimal.ZERO;
        return this.price.multiply(BigDecimal.valueOf(this.quantity));
    }

    public void validate() {
        if (this.orderId == null) throw new IllegalArgumentException("orderId required");
        if (this.quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (this.price == null || this.price.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("price must be > 0");
        if (this.executedAt == null) throw new IllegalArgumentException("executedAt required");
    }

    @Override
    public String toString() {
        return "Execution{" +
                "orderId=" + orderId +
                ", quantity=" + quantity +
                ", price=" + price +
                ", executedAt=" + executedAt +
                '}';
    }
}
package com.tradingsystem.event.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Payload for ORDER_FILLED, ORDER_REJECTED, ORDER_CANCELLED events on the trade-events topic.
 * Follows the contract defined in kafka-topics.md
 */
public class TradeEventPayload {
    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("accountId")
    private Long accountId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("side")
    private String side;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("executedPrice")
    private BigDecimal executedPrice;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("cashDelta")
    private BigDecimal cashDelta;

    @JsonProperty("positionQuantityAfter")
    private Integer positionQuantityAfter;

    @JsonProperty("averageCostAfter")
    private BigDecimal averageCostAfter;

    @JsonProperty("executedOn")
    private String executedOn;

    public TradeEventPayload() {}

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public BigDecimal getCashDelta() { return cashDelta; }
    public void setCashDelta(BigDecimal cashDelta) { this.cashDelta = cashDelta; }

    public Integer getPositionQuantityAfter() { return positionQuantityAfter; }
    public void setPositionQuantityAfter(Integer positionQuantityAfter) { this.positionQuantityAfter = positionQuantityAfter; }

    public BigDecimal getAverageCostAfter() { return averageCostAfter; }
    public void setAverageCostAfter(BigDecimal averageCostAfter) { this.averageCostAfter = averageCostAfter; }

    public String getExecutedOn() { return executedOn; }
    public void setExecutedOn(String executedOn) { this.executedOn = executedOn; }
}



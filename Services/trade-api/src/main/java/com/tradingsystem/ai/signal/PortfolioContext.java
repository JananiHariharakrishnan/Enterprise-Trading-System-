package com.tradingsystem.ai.signal;

import java.math.BigDecimal;

/**
 * Encapsulates the user's real-time portfolio scenario when evaluating a trade signal:
 * account cash, buying power, current holdings, allocation weight, and order details.
 */
public class PortfolioContext {

    private Long accountId;
    private BigDecimal cashBalance;
    private BigDecimal buyingPower;
    private int currentHoldingQuantity;
    private BigDecimal currentAverageCost;
    private Double portfolioAllocationPct;
    private String orderSide; // "BUY" or "SELL"
    private int orderQuantity;
    private BigDecimal orderPrice;

    public PortfolioContext() {}

    public PortfolioContext(
            Long accountId,
            BigDecimal cashBalance,
            BigDecimal buyingPower,
            int currentHoldingQuantity,
            BigDecimal currentAverageCost,
            Double portfolioAllocationPct,
            String orderSide,
            int orderQuantity,
            BigDecimal orderPrice) {
        this.accountId = accountId;
        this.cashBalance = cashBalance;
        this.buyingPower = buyingPower;
        this.currentHoldingQuantity = currentHoldingQuantity;
        this.currentAverageCost = currentAverageCost;
        this.portfolioAllocationPct = portfolioAllocationPct;
        this.orderSide = orderSide;
        this.orderQuantity = orderQuantity;
        this.orderPrice = orderPrice;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public BigDecimal getBuyingPower() {
        return buyingPower;
    }

    public void setBuyingPower(BigDecimal buyingPower) {
        this.buyingPower = buyingPower;
    }

    public int getCurrentHoldingQuantity() {
        return currentHoldingQuantity;
    }

    public void setCurrentHoldingQuantity(int currentHoldingQuantity) {
        this.currentHoldingQuantity = currentHoldingQuantity;
    }

    public BigDecimal getCurrentAverageCost() {
        return currentAverageCost;
    }

    public void setCurrentAverageCost(BigDecimal currentAverageCost) {
        this.currentAverageCost = currentAverageCost;
    }

    public Double getPortfolioAllocationPct() {
        return portfolioAllocationPct;
    }

    public void setPortfolioAllocationPct(Double portfolioAllocationPct) {
        this.portfolioAllocationPct = portfolioAllocationPct;
    }

    public String getOrderSide() {
        return orderSide;
    }

    public void setOrderSide(String orderSide) {
        this.orderSide = orderSide;
    }

    public int getOrderQuantity() {
        return orderQuantity;
    }

    public void setOrderQuantity(int orderQuantity) {
        this.orderQuantity = orderQuantity;
    }

    public BigDecimal getOrderPrice() {
        return orderPrice;
    }

    public void setOrderPrice(BigDecimal orderPrice) {
        this.orderPrice = orderPrice;
    }

    public String toPromptString(String symbol) {
        StringBuilder sb = new StringBuilder();
        if (buyingPower != null) {
            sb.append(String.format("- Account Buying Power: $%.2f (Cash Balance: $%.2f)%n",
                    buyingPower.doubleValue(),
                    cashBalance != null ? cashBalance.doubleValue() : 0.0));
        }
        if (currentHoldingQuantity > 0) {
            sb.append(String.format("- Current Portfolio Position in %s: %d shares @ avg cost $%.2f",
                    symbol, currentHoldingQuantity,
                    currentAverageCost != null ? currentAverageCost.doubleValue() : 0.0));
            if (portfolioAllocationPct != null && portfolioAllocationPct > 0) {
                sb.append(String.format(" (%.1f%% portfolio weight)", portfolioAllocationPct));
            }
            sb.append("\n");
        } else {
            sb.append(String.format("- Current Portfolio Position in %s: 0 shares (new position entry)%n", symbol));
        }
        if (orderSide != null && orderQuantity > 0) {
            sb.append(String.format("- Order Placed by User: %s %d shares%s%n",
                    orderSide, orderQuantity,
                    orderPrice != null ? String.format(" @ $%.2f", orderPrice.doubleValue()) : ""));
        }
        return sb.toString();
    }
}

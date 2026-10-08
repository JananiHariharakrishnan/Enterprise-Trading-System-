package com.tradingsystem.dto.response;

import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.model.StrategyType;

import java.math.BigDecimal;

public class StrategyResponse {

    private String strategyId;
    private Long accountId;
    private String symbol;
    private StrategyType type;
    private int quantity;
    private boolean enabled;
    private BigDecimal currentSpend;
    private BigDecimal maxSpend;
    private int maxPositionSize;
    private int consecutiveFailures;
    private int maxConsecutiveFailures;

    public StrategyResponse() {}

    public static StrategyResponse fromInstance(StrategyInstance instance) {
        StrategyResponse response = new StrategyResponse();
        response.setStrategyId(instance.getStrategyId());
        response.setAccountId(instance.getAccountId());
        response.setSymbol(instance.getSymbol());
        response.setType(instance.getType());
        response.setQuantity(instance.getQuantity());
        response.setEnabled(instance.isEnabled());
        response.setCurrentSpend(instance.getCurrentSpend());
        response.setMaxSpend(instance.getMaxSpend());
        response.setMaxPositionSize(instance.getMaxPositionSize());
        response.setConsecutiveFailures(instance.getConsecutiveFailures());
        response.setMaxConsecutiveFailures(instance.getMaxConsecutiveFailures());
        return response;
    }

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public StrategyType getType() { return type; }
    public void setType(StrategyType type) { this.type = type; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public BigDecimal getCurrentSpend() { return currentSpend; }
    public void setCurrentSpend(BigDecimal currentSpend) { this.currentSpend = currentSpend; }

    public BigDecimal getMaxSpend() { return maxSpend; }
    public void setMaxSpend(BigDecimal maxSpend) { this.maxSpend = maxSpend; }

    public int getMaxPositionSize() { return maxPositionSize; }
    public void setMaxPositionSize(int maxPositionSize) { this.maxPositionSize = maxPositionSize; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public int getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) { this.maxConsecutiveFailures = maxConsecutiveFailures; }
}


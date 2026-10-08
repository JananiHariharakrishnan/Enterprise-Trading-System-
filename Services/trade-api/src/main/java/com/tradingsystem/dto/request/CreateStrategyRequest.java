package com.tradingsystem.dto.request;

import com.tradingsystem.automation.model.StrategyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateStrategyRequest {

    private String strategyId;
    private Long accountId;

    @NotBlank
    private String symbol;

    @NotNull
    private StrategyType type;

    @NotNull
    @Min(1)
    private Integer quantity;

    private Integer fastPeriod;
    private Integer slowPeriod;
    private Integer bbPeriod;
    private Double bbStdDev;

    private BigDecimal maxSpend;
    private Integer maxPositionSize;
    private Integer maxConsecutiveFailures;

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public StrategyType getType() { return type; }
    public void setType(StrategyType type) { this.type = type; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getFastPeriod() { return fastPeriod; }
    public void setFastPeriod(Integer fastPeriod) { this.fastPeriod = fastPeriod; }

    public Integer getSlowPeriod() { return slowPeriod; }
    public void setSlowPeriod(Integer slowPeriod) { this.slowPeriod = slowPeriod; }

    public Integer getBbPeriod() { return bbPeriod; }
    public void setBbPeriod(Integer bbPeriod) { this.bbPeriod = bbPeriod; }

    public Double getBbStdDev() { return bbStdDev; }
    public void setBbStdDev(Double bbStdDev) { this.bbStdDev = bbStdDev; }

    public BigDecimal getMaxSpend() { return maxSpend; }
    public void setMaxSpend(BigDecimal maxSpend) { this.maxSpend = maxSpend; }

    public Integer getMaxPositionSize() { return maxPositionSize; }
    public void setMaxPositionSize(Integer maxPositionSize) { this.maxPositionSize = maxPositionSize; }

    public Integer getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public void setMaxConsecutiveFailures(Integer maxConsecutiveFailures) { this.maxConsecutiveFailures = maxConsecutiveFailures; }
}


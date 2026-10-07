package com.tradingsystem.model;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class PriceAlert {

    private Long id;
    private Long accountId;
    private String symbol;
    private AlertDirection direction;
    private BigDecimal threshold;
    private AlertStatus status;
    private OffsetDateTime triggeredOn;
    private OffsetDateTime createdOn;
    private OffsetDateTime updatedOn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public AlertDirection getDirection() {
        return direction;
    }

    public void setDirection(AlertDirection direction) {
        this.direction = direction;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public OffsetDateTime getTriggeredOn() {
        return triggeredOn;
    }

    public void setTriggeredOn(OffsetDateTime triggeredOn) {
        this.triggeredOn = triggeredOn;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(OffsetDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public OffsetDateTime getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(OffsetDateTime updatedOn) {
        this.updatedOn = updatedOn;
    }

    public boolean isActive() {
        return status == AlertStatus.ACTIVE;
    }

    public void validate() {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId required");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol required");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction required");
        }
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
    }
}


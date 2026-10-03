package com.tradingsystem.model;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.enums.NotificationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceAlert {

    private Long id;
    private Long accountId;
    private Long watchlistId;
    private String symbol;
    private AlertDirection direction;
    private BigDecimal threshold;
    private AlertStatus status;
    private boolean lastConditionMet;
    private BigDecimal lastEvaluatedPrice;
    private LocalDateTime lastQuoteAsOf;
    private LocalDateTime triggeredOn;
    private NotificationStatus notificationStatus;
    private int notificationAttempts;
    private String lastNotificationError;
    private LocalDateTime createdOn;
    private LocalDateTime updatedOn;

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

    public Long getWatchlistId() {
        return watchlistId;
    }

    public void setWatchlistId(Long watchlistId) {
        this.watchlistId = watchlistId;
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

    public boolean isLastConditionMet() {
        return lastConditionMet;
    }

    public void setLastConditionMet(boolean lastConditionMet) {
        this.lastConditionMet = lastConditionMet;
    }

    public BigDecimal getLastEvaluatedPrice() {
        return lastEvaluatedPrice;
    }

    public void setLastEvaluatedPrice(BigDecimal lastEvaluatedPrice) {
        this.lastEvaluatedPrice = lastEvaluatedPrice;
    }

    public LocalDateTime getLastQuoteAsOf() {
        return lastQuoteAsOf;
    }

    public void setLastQuoteAsOf(LocalDateTime lastQuoteAsOf) {
        this.lastQuoteAsOf = lastQuoteAsOf;
    }

    public LocalDateTime getTriggeredOn() {
        return triggeredOn;
    }

    public void setTriggeredOn(LocalDateTime triggeredOn) {
        this.triggeredOn = triggeredOn;
    }

    public NotificationStatus getNotificationStatus() {
        return notificationStatus;
    }

    public void setNotificationStatus(NotificationStatus notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    public int getNotificationAttempts() {
        return notificationAttempts;
    }

    public void setNotificationAttempts(int notificationAttempts) {
        this.notificationAttempts = notificationAttempts;
    }

    public String getLastNotificationError() {
        return lastNotificationError;
    }

    public void setLastNotificationError(String lastNotificationError) {
        this.lastNotificationError = lastNotificationError;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public LocalDateTime getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(LocalDateTime updatedOn) {
        this.updatedOn = updatedOn;
    }

    public boolean isActive() {
        return status == AlertStatus.ACTIVE;
    }

    public void validate() {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId required");
        }
        if (watchlistId == null || watchlistId <= 0) {
            throw new IllegalArgumentException("watchlistId required");
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


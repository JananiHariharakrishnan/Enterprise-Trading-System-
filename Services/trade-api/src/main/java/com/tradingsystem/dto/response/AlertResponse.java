package com.tradingsystem.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AlertResponse {

    private Long id;
    private String symbol;
    private String direction;
    private BigDecimal threshold;
    private String status;
    private OffsetDateTime triggeredOn;
    private OffsetDateTime createdOn;
    private OffsetDateTime updatedOn;

    public AlertResponse() {
        // Empty constructor
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
}

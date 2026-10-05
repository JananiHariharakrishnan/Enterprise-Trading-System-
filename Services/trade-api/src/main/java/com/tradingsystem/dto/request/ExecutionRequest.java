package com.tradingsystem.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body sent by the executor to the trade-api to apply an execution decision.
 */
public class ExecutionRequest {

    private String status; // "FILLED" or "REJECTED"
    private BigDecimal executedPrice; // optional for REJECTED
    private String reason; // optional
    private UUID eventId; // optional: Kafka event ID for idempotency tracking

    public ExecutionRequest() {
        //Default constructor required by Jackson for JSON deserialization.
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
}
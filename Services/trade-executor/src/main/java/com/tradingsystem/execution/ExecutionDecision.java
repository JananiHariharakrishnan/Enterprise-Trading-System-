package com.tradingsystem.execution;

import java.math.BigDecimal;

public class ExecutionDecision {

    public enum Status {
        FILLED,
        REJECTED
    }

    private final Status status;
    private final BigDecimal executedPrice;
    private final String reason;

    private ExecutionDecision(
            Status status,
            BigDecimal executedPrice,
            String reason) {

        this.status = status;
        this.executedPrice = executedPrice;
        this.reason = reason;
    }
    public static ExecutionDecision filled(BigDecimal price){

        return new ExecutionDecision(
                Status.FILLED,
                price,
                null
        );
    }

    public static ExecutionDecision rejected(String reason) {
        return new ExecutionDecision(
                Status.REJECTED,
                null,
                reason
        );
    }
    public Status getStatus(){
        return status;
    }
    public BigDecimal getExecutedPrice(){
        return executedPrice;
    }
    public String getReason(){
        return reason;
    }
}
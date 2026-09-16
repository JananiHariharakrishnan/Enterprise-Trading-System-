package com.tradingsystem.execution;

import java.math.BigDecimal;

public class ExecutionRule {

    public ExecutionDecision decide(
            String side,
            BigDecimal orderPrice,
            BigDecimal quotePrice) {

        if (side == null || orderPrice == null || quotePrice == null) {
            return ExecutionDecision.rejected(
                    "Missing execution price information"
            );
        }

        if ("BUY".equalsIgnoreCase(side)) {

            if (orderPrice.compareTo(quotePrice) >= 0) {
                return ExecutionDecision.filled(quotePrice);
            }

            return ExecutionDecision.rejected(
                    "BUY order is not marketable"
            );
        }

        if ("SELL".equalsIgnoreCase(side)) {

            if (orderPrice.compareTo(quotePrice) <= 0) {
                return ExecutionDecision.filled(quotePrice);
            }

            return ExecutionDecision.rejected(
                    "SELL order is not marketable"
            );
        }

        return ExecutionDecision.rejected(
                "Unsupported order side: " + side
        );
    }
}
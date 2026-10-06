package com.tradingsystem.execution;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class ExecutionRule {

    public ExecutionDecision decide(
            String side,
            BigDecimal orderPrice,
            BigDecimal quotePrice) {

        if (side == null || quotePrice == null) {
            return ExecutionDecision.rejected(
                    "Missing execution price information"
            );
        }

        // MARKET order: orderPrice is NULL, execute immediately at quote price
        if (orderPrice == null) {
            return ExecutionDecision.filled(quotePrice);
        }

        // LIMIT order: orderPrice is provided, check if quote is favorable
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
                "Unsupported order side"
        );
    }
}
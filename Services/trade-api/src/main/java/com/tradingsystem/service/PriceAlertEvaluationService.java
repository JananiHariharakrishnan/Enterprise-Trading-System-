package com.tradingsystem.service;

import java.math.BigDecimal;

/**
 * Service for evaluating price alerts against incoming market data quotes.
 * 
 * Evaluates ACTIVE alerts to determine if price has crossed the threshold.
 * Transitions alerts from ACTIVE to TRIGGERED when crossing conditions are met.
 * 
 * NOT responsible for notifications or user delivery.
 */
public interface PriceAlertEvaluationService {

    /**
     * Evaluate all ACTIVE alerts for a symbol against new price data.
     *
     * For ABOVE alerts:
     *   - Triggers when previousPrice < threshold AND currentPrice >= threshold
     * 
     * For BELOW alerts:
     *   - Triggers when previousPrice > threshold AND currentPrice <= threshold
     *
     * Does NOT trigger repeatedly while price remains beyond threshold.
     *
     * @param symbol the instrument symbol
     * @param previousPrice price before this update (null if first price for symbol)
     * @param currentPrice current price from market data
     */
    void evaluateAlertsForSymbol(
            String symbol,
            BigDecimal previousPrice,
            BigDecimal currentPrice
    );
}

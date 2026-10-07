package com.tradingsystem.service.Impl;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.mapper.PriceAlertDataMapper;
import com.tradingsystem.model.PriceAlert;
import com.tradingsystem.service.PriceAlertEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evaluates ACTIVE price alerts when market data arrives.
 * 
 * For each quote, checks all ACTIVE alerts for that symbol:
 * - ABOVE alerts trigger when price crosses above threshold
 * - BELOW alerts trigger when price crosses below threshold
 * 
 * Transitions triggered alerts from ACTIVE to TRIGGERED.
 * Does NOT trigger repeatedly while price stays beyond threshold.
 */
@Service
public class PriceAlertEvaluationServiceImpl implements PriceAlertEvaluationService {

    private static final Logger logger =
            LoggerFactory.getLogger(PriceAlertEvaluationServiceImpl.class);

    private final PriceAlertDataMapper priceAlertDataMapper;

    public PriceAlertEvaluationServiceImpl(PriceAlertDataMapper priceAlertDataMapper) {
        this.priceAlertDataMapper = priceAlertDataMapper;
    }

    @Override
    @Transactional
    public void evaluateAlertsForSymbol(
            String symbol,
            BigDecimal previousPrice,
            BigDecimal currentPrice
    ) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }

        if (currentPrice == null) {
            return;
        }

        // If no previous price (first quote for this symbol), can't evaluate crossing
        if (previousPrice == null) {
            logger.debug(
                    "No previous price for symbol {}, skipping alert evaluation",
                    symbol
            );
            return;
        }

        // Find all ACTIVE alerts for this symbol
        List<PriceAlert> activeAlerts = priceAlertDataMapper.findActiveAlertsBySymbol(symbol);

        if (activeAlerts == null || activeAlerts.isEmpty()) {
            return;
        }

        for (PriceAlert alert : activeAlerts) {
            evaluateAlert(alert, previousPrice, currentPrice);
        }
    }

    /**
     * Evaluate a single alert against price movement.
     *
     * ABOVE: triggers when previousPrice < threshold AND currentPrice >= threshold
     * BELOW: triggers when previousPrice > threshold AND currentPrice <= threshold
     *
     * Uses atomic database update to ensure alerts trigger exactly once,
     * even with duplicate market-data events from Kafka.
     *
     * @param alert the alert to evaluate
     * @param previousPrice the previous price
     * @param currentPrice the current price
     */
    private void evaluateAlert(
            PriceAlert alert,
            BigDecimal previousPrice,
            BigDecimal currentPrice
    ) {
        boolean shouldTrigger = false;

        if (alert.getDirection() == AlertDirection.ABOVE) {
            // Trigger if price crosses above threshold
            shouldTrigger = previousPrice.compareTo(alert.getThreshold()) < 0
                    && currentPrice.compareTo(alert.getThreshold()) >= 0;

            if (shouldTrigger) {
                logger.info(
                        "Alert {} triggered: {} crossed above threshold {} (was {})",
                        alert.getId(),
                        alert.getSymbol(),
                        alert.getThreshold(),
                        previousPrice
                );
            }
        } else if (alert.getDirection() == AlertDirection.BELOW) {
            // Trigger if price crosses below threshold
            shouldTrigger = previousPrice.compareTo(alert.getThreshold()) > 0
                    && currentPrice.compareTo(alert.getThreshold()) <= 0;

            if (shouldTrigger) {
                logger.info(
                        "Alert {} triggered: {} crossed below threshold {} (was {})",
                        alert.getId(),
                        alert.getSymbol(),
                        alert.getThreshold(),
                        previousPrice
                );
            }
        }

        // Update alert status if trigger condition met
        // Uses atomic transition to ensure at-most-once triggering
        if (shouldTrigger) {
            OffsetDateTime triggeredOn = OffsetDateTime.now();
            
            int rowsUpdated = priceAlertDataMapper.transitionAlertToTriggered(
                    alert.getAccountId(),
                    alert.getId(),
                    triggeredOn
            );

            if (rowsUpdated > 0) {
                // Alert was successfully transitioned to TRIGGERED
                logger.debug(
                        "Alert {} state transitioned to TRIGGERED at {}",
                        alert.getId(),
                        triggeredOn
                );
            } else {
                // Alert was already triggered (or cancelled/etc) - idempotency
                logger.debug(
                        "Alert {} was not in ACTIVE state, skipping transition (likely duplicate event)",
                        alert.getId()
                );
            }
        }
    }
}

package com.tradingsystem.mapper;

import com.tradingsystem.model.PriceAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PriceAlertDataMapper {

    /**
     * Create a new price alert.
     */
    int insertPriceAlert(PriceAlert priceAlert);

    /**
     * Find all alerts for an account, optionally filtered by status.
     */
    List<PriceAlert> findAlertsByAccountId(
            @Param("accountId") Long accountId,
            @Param("status") String status
    );

    /**
     * Find a specific alert by ID (simple read without auth check).
     */
    PriceAlert findAlertById(@Param("alertId") Long alertId);

    /**
     * Find alert ensuring the account owns it.
     * Used for mutations (update, delete) to enforce authorization.
     */
    PriceAlert findAlertByAccountIdAndId(
            @Param("accountId") Long accountId,
            @Param("alertId") Long alertId
    );

    /**
     * Delete an alert, ensuring account ownership.
     */
    int deleteAlert(
            @Param("accountId") Long accountId,
            @Param("alertId") Long alertId
    );

    /**
     * Count active alerts for a customer.
     */
    int countActiveAlertsByAccountId(@Param("accountId") Long accountId);

    /**
     * Find all ACTIVE alerts for a given symbol.
     * Used by the market-data evaluator in the hot path.
     */
    List<PriceAlert> findActiveAlertsBySymbol(@Param("symbol") String symbol);

    /**
     * Update alert status and triggered_on timestamp.
     * Called when an alert is triggered.
     */
    int updateAlertStatus(
            @Param("accountId") Long accountId,
            @Param("alertId") Long alertId,
            @Param("status") String status,
            @Param("triggeredOn") java.time.OffsetDateTime triggeredOn
    );

    /**
     * Atomic transition: ACTIVE → TRIGGERED.
     * 
     * Safe for at-least-once Kafka delivery.
     * Only succeeds if alert is still ACTIVE; returns 0 if already triggered.
     * 
     * @return 1 if alert was transitioned to TRIGGERED, 0 if already in another state
     */
    int transitionAlertToTriggered(
            @Param("accountId") Long accountId,
            @Param("alertId") Long alertId,
            @Param("triggeredOn") java.time.OffsetDateTime triggeredOn
    );
}


package com.tradingsystem.service.impl;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.mapper.PriceAlertDataMapper;
import com.tradingsystem.model.PriceAlert;
import com.tradingsystem.model.User;
import com.tradingsystem.service.Impl.PriceAlertEvaluationServiceImpl;
import com.tradingsystem.service.NotificationService;
import com.tradingsystem.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluationServiceImplTest {

    @Mock
    private PriceAlertDataMapper priceAlertDataMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    private PriceAlertEvaluationServiceImpl evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new PriceAlertEvaluationServiceImpl(
                priceAlertDataMapper,
                notificationService,
                userService
        );
    }

    // =========================================================
    // ABOVE THRESHOLD TESTS
    // =========================================================

    @Test
    void evaluateAlerts_aboveThreshold_noCrossingBelowThreshold_doesNotTrigger() {
        // Price stays below threshold: 328 -> 329
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("328"),
                new BigDecimal("329")
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_aboveThreshold_crossesAbove_triggers() {
        // Price crosses above threshold: 329 -> 331
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_aboveThreshold_noCrossingAboveThreshold_doesNotTrigger() {
        // Price stays above threshold: 331 -> 335
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("335")
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_aboveThreshold_hitsThresholdExactly_triggers() {
        // Price equals threshold
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("330")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_aboveThreshold_crossesTooHigh_triggers() {
        // Price crosses above with large jump: 328 -> 500
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("328"),
                new BigDecimal("500")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    // =========================================================
    // BELOW THRESHOLD TESTS
    // =========================================================

    @Test
    void evaluateAlerts_belowThreshold_noCrossingAboveThreshold_doesNotTrigger() {
        // Price stays above threshold: 331 -> 335
        PriceAlert alert = createBelowAlert(1001L, 2L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("335")
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_belowThreshold_crossesBelow_triggers() {
        // Price crosses below threshold: 331 -> 329
        PriceAlert alert = createBelowAlert(1001L, 2L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(2L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("329")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(2L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_belowThreshold_noCrossingBelowThreshold_doesNotTrigger() {
        // Price stays below threshold: 328 -> 329
        PriceAlert alert = createBelowAlert(1001L, 2L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("328"),
                new BigDecimal("329")
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_belowThreshold_hitsThresholdExactly_triggers() {
        // Price equals threshold
        PriceAlert alert = createBelowAlert(1001L, 2L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(2L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("330")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(2L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_belowThreshold_crossesTooLow_triggers() {
        // Price crosses below with large drop: 332 -> 10
        PriceAlert alert = createBelowAlert(1001L, 2L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(2L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("332"),
                new BigDecimal("10")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(2L),
                any(OffsetDateTime.class)
        );
    }

    // =========================================================
    // MULTIPLE ALERTS AND EDGE CASES
    // =========================================================

    @Test
    void evaluateAlerts_multipleAlertsOneSymbol_triggersOnlyMatching() {
        // Two alerts on same symbol: one ABOVE 330, one BELOW 320
        // Price 325 -> 331 should trigger ABOVE alert only
        PriceAlert aboveAlert = createAboveAlert(1001L, 1L, "AAPL", 330);
        PriceAlert belowAlert = createBelowAlert(1001L, 2L, "AAPL", 320);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(aboveAlert, belowAlert));

        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // 1 row updated (success)

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("325"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );

        verify(priceAlertDataMapper, never())
                .transitionAlertToTriggered(eq(1001L), eq(2L), any());
    }

    @Test
    void evaluateAlerts_multipleAlertsBothTrigger() {
        // Two alerts: one ABOVE 150, one BELOW 160
        // Price 155 -> 170 should trigger ABOVE alert only
        // Price 155 -> 140 should trigger BELOW alert only
        PriceAlert aboveAlert = createAboveAlert(1001L, 1L, "AAPL", 150);
        PriceAlert belowAlert = createBelowAlert(1001L, 2L, "AAPL", 160);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(aboveAlert, belowAlert));

        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // 1 row updated (success)

        // Price crosses above 150
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("145"),
                new BigDecimal("155")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_nullPreviousPrice_skipsEvaluation() {
        // First quote for symbol (no previous price)
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                null,
                new BigDecimal("325")
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_nullCurrentPrice_skipsEvaluation() {
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("325"),
                null
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_nullOrBlankSymbol_skipsEvaluation() {
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);

        evaluationService.evaluateAlertsForSymbol(
                null,
                new BigDecimal("325"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper, never()).findActiveAlertsBySymbol(any());

        evaluationService.evaluateAlertsForSymbol(
                "   ",
                new BigDecimal("325"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper, never()).findActiveAlertsBySymbol("   ");
    }

    @Test
    void evaluateAlerts_noActiveAlerts_doesNothing() {
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of());

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("325"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(any(), any(), any(), any());
    }

    @Test
    void evaluateAlerts_differentSymbols_evaluatesCorrectly() {
        // Two alerts on different symbols
        PriceAlert aapl = createAboveAlert(1001L, 1L, "AAPL", 330);
        PriceAlert msft = createBelowAlert(1001L, 2L, "MSFT", 400);
        
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(aapl));
        when(priceAlertDataMapper.findActiveAlertsBySymbol("MSFT"))
                .thenReturn(List.of(msft));

        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);

        // Evaluate AAPL
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );

        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(2L), any()))
                .thenReturn(1);

        // Evaluate MSFT
        evaluationService.evaluateAlertsForSymbol(
                "MSFT",
                new BigDecimal("401"),
                new BigDecimal("399")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(2L),
                any(OffsetDateTime.class)
        );
    }

    // =========================================================
    // SAFE STATE TRANSITION & IDEMPOTENCY TESTS
    // =========================================================

    @Test
    void evaluateAlerts_atomicTransition_successfulTrigger() {
        // Atomic transition succeeds (alert is ACTIVE)
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // 1 row updated = success

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_duplicateEvent_idempotent() {
        // Same alert evaluated twice (duplicate market-data event)
        // First evaluation: succeeds (1 row updated)
        // Second evaluation: fails (0 rows updated - alert no longer ACTIVE)
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // First call succeeds
        
        // First evaluation
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );

        // Reset mocks for second call
        reset(priceAlertDataMapper);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of());  // No ACTIVE alerts (already triggered)
        
        // Second evaluation with same alert (duplicate event)
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // No transition attempt because alert is no longer ACTIVE
        verify(priceAlertDataMapper, never()).transitionAlertToTriggered(any(), any(), any());
    }

    @Test
    void evaluateAlerts_alreadyTriggered_preventReTriggering() {
        // Alert triggers crossing, then price falls back, price crosses again
        // Should not re-trigger because alert is now TRIGGERED (not ACTIVE)
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);

        // First crossing: 329 -> 331
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // Success

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );

        // Price falls back: 331 -> 329 (would trigger again if alert was still ACTIVE)
        reset(priceAlertDataMapper);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of());  // Alert no longer ACTIVE

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("329")
        );

        // No transition because alert is not in active list
        verify(priceAlertDataMapper, never()).transitionAlertToTriggered(any(), any(), any());
    }

    @Test
    void evaluateAlerts_multipleCancelledAlerts_skipsNonActive() {
        // Two alerts on same symbol: one ACTIVE, one CANCELLED
        // Only ACTIVE should be evaluated
        PriceAlert activeAlert = createAboveAlert(1001L, 1L, "AAPL", 330);
        // Cancelled alert would not appear in findActiveAlertsBySymbol results
        
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(activeAlert));  // Only ACTIVE alerts
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // Only active alert is transitioned
        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_multipleAccountsSameSymbol_scoped() {
        // Two accounts with alerts on same symbol
        PriceAlert acct1Alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        PriceAlert acct2Alert = createAboveAlert(2001L, 2L, "AAPL", 330);
        
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(acct1Alert, acct2Alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(2001L), eq(2L), any()))
                .thenReturn(1);

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // Both accounts' alerts are transitioned
        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(2001L),
                eq(2L),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_concurrentDuplicateEvents_onlyOneTriggers() {
        // Simulates race condition: two threads evaluate same crossing
        // Both attempt transition, but database ensures only one succeeds
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        
        // First thread: succeeds
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // Update mock for second thread: fails
        reset(priceAlertDataMapper);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(0);  // 0 rows updated = already triggered

        // Second thread attempts same evaluation
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // Attempted transition, but failed (idempotent)
        verify(priceAlertDataMapper).transitionAlertToTriggered(
                eq(1001L),
                eq(1L),
                any(OffsetDateTime.class)
        );
    }

    // =========================================================
    // NOTIFICATION TESTS
    // =========================================================

    @Test
    void evaluateAlerts_notifiesCustomerAfterSuccessfulCrossing() {
        // Alert crosses threshold and is successfully transitioned
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        User user = createTestUser(1001L, "trader1", "alice@example.com");

        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // Success
        when(userService.getUserByAccountId(1001L))
                .thenReturn(user);

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // Verify notification was called with correct parameters
        verify(notificationService).sendPriceAlertTriggeredNotification(
                eq(user),
                eq("AAPL"),
                eq("ABOVE"),
                eq(new BigDecimal("330")),
                eq(new BigDecimal("331")),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_noNotificationBeforeCrossing() {
        // No crossing (price stays below threshold) - no evaluation, no notification
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("328"),
                new BigDecimal("329")
        );

        // No notification sent because alert didn't trigger
        verify(notificationService, never()).sendPriceAlertTriggeredNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void evaluateAlerts_noNotificationWhenNoPreviousPrice() {
        // First quote for symbol (no previous price) - no evaluation, no notification
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                null,
                new BigDecimal("330")
        );

        // No notification sent
        verify(notificationService, never()).sendPriceAlertTriggeredNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void evaluateAlerts_noDuplicateNotificationOnIdempotentTransition() {
        // Same alert crosses threshold twice (duplicate Kafka event)
        // First evaluation succeeds, second returns 0 (already triggered)
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        User user = createTestUser(1001L, "trader1", "alice@example.com");

        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // First call succeeds
        when(userService.getUserByAccountId(1001L))
                .thenReturn(user);

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // First notification sent
        verify(notificationService, times(1)).sendPriceAlertTriggeredNotification(
                any(), any(), any(), any(), any(), any()
        );

        // Second evaluation (duplicate event)
        reset(priceAlertDataMapper, notificationService);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(0);  // Already triggered

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // No second notification (transition returned 0)
        verify(notificationService, never()).sendPriceAlertTriggeredNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void evaluateAlerts_notificationFailureDoesNotCrash() {
        // Notification service throws exception - should be caught
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        User user = createTestUser(1001L, "trader1", "alice@example.com");

        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(1001L), eq(1L), any()))
                .thenReturn(1);  // Success
        when(userService.getUserByAccountId(1001L))
                .thenReturn(user);
        
        // Notification service throws exception
        doThrow(new RuntimeException("Email service unavailable"))
                .when(notificationService).sendPriceAlertTriggeredNotification(
                        any(), any(), any(), any(), any(), any()
                );

        // Should not throw - exception is caught and logged
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // Notification was attempted
        verify(notificationService).sendPriceAlertTriggeredNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void evaluateAlerts_notificationSkippedWhenUserNotFound() {
        // User lookup fails (account exists but user doesn't in user table)
        PriceAlert alert = createAboveAlert(9999L, 1L, "AAPL", 330);

        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));
        when(priceAlertDataMapper.transitionAlertToTriggered(eq(9999L), eq(1L), any()))
                .thenReturn(1);  // Alert state changed
        when(userService.getUserByAccountId(9999L))
                .thenReturn(null);  // User not found

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        // No notification sent (user not found)
        verify(notificationService, never()).sendPriceAlertTriggeredNotification(
                any(), any(), any(), any(), any(), any()
        );
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private PriceAlert createAboveAlert(Long accountId, Long alertId, String symbol, int threshold) {
        PriceAlert alert = new PriceAlert();
        alert.setId(alertId);
        alert.setAccountId(accountId);
        alert.setSymbol(symbol);
        alert.setDirection(AlertDirection.ABOVE);
        alert.setThreshold(new BigDecimal(threshold));
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedOn(OffsetDateTime.now());
        alert.setUpdatedOn(OffsetDateTime.now());
        return alert;
    }

    private PriceAlert createBelowAlert(Long accountId, Long alertId, String symbol, int threshold) {
        PriceAlert alert = new PriceAlert();
        alert.setId(alertId);
        alert.setAccountId(accountId);
        alert.setSymbol(symbol);
        alert.setDirection(AlertDirection.BELOW);
        alert.setThreshold(new BigDecimal(threshold));
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedOn(OffsetDateTime.now());
        alert.setUpdatedOn(OffsetDateTime.now());
        return alert;
    }

    private User createTestUser(Long accountId, String username, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAccountId(accountId);
        user.setUsername(username);
        user.setFirstName("Test");
        user.setEmail(email);
        return user;
    }
}

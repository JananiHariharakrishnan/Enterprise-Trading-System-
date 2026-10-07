package com.tradingsystem.service.impl;

import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.mapper.PriceAlertDataMapper;
import com.tradingsystem.model.PriceAlert;
import com.tradingsystem.service.Impl.PriceAlertEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluationServiceImplTest {

    @Mock
    private PriceAlertDataMapper priceAlertDataMapper;

    private PriceAlertEvaluationServiceImpl evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new PriceAlertEvaluationServiceImpl(priceAlertDataMapper);
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

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                1L,
                "TRIGGERED",
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

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("330")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                1L,
                "TRIGGERED",
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_aboveThreshold_crossesTooHigh_triggers() {
        // Price crosses above with large jump: 328 -> 500
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("328"),
                new BigDecimal("500")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                1L,
                "TRIGGERED",
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

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("329")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                2L,
                "TRIGGERED",
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

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("331"),
                new BigDecimal("330")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                2L,
                "TRIGGERED",
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_belowThreshold_crossesTooLow_triggers() {
        // Price crosses below with large drop: 332 -> 10
        PriceAlert alert = createBelowAlert(1001L, 2L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("332"),
                new BigDecimal("10")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                2L,
                "TRIGGERED",
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

        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("325"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                1L,
                "TRIGGERED",
                any(OffsetDateTime.class)
        );

        verify(priceAlertDataMapper, never())
                .updateAlertStatus(eq(1001L), eq(2L), any(), any());
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

        // Price crosses above 150
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("145"),
                new BigDecimal("155")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                1L,
                "TRIGGERED",
                any(OffsetDateTime.class)
        );
    }

    @Test
    void evaluateAlerts_nullPreviousPrice_skipsEvaluation() {
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

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
        PriceAlert alert = createAboveAlert(1001L, 1L, "AAPL", 330);
        when(priceAlertDataMapper.findActiveAlertsBySymbol("AAPL"))
                .thenReturn(List.of(alert));

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

        // Evaluate AAPL
        evaluationService.evaluateAlertsForSymbol(
                "AAPL",
                new BigDecimal("329"),
                new BigDecimal("331")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                1L,
                "TRIGGERED",
                any(OffsetDateTime.class)
        );

        // Evaluate MSFT
        evaluationService.evaluateAlertsForSymbol(
                "MSFT",
                new BigDecimal("401"),
                new BigDecimal("399")
        );

        verify(priceAlertDataMapper).updateAlertStatus(
                1001L,
                2L,
                "TRIGGERED",
                any(OffsetDateTime.class)
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
}

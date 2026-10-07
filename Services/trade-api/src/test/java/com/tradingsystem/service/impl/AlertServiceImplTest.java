package com.tradingsystem.service.impl;

import com.tradingsystem.dto.request.CreateAlertRequest;
import com.tradingsystem.dto.response.AlertResponse;
import com.tradingsystem.enums.AccountStatus;
import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.AlertLimitExceededException;
import com.tradingsystem.exception.AlertNotFoundException;
import com.tradingsystem.exception.AlertStateException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.InstrumentDataMapper;
import com.tradingsystem.mapper.PriceAlertDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.PriceAlert;
import com.tradingsystem.service.Impl.AlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private PriceAlertDataMapper priceAlertDataMapper;

    @Mock
    private AccountDataMapper accountDataMapper;

    @Mock
    private InstrumentDataMapper instrumentDataMapper;

    private AlertServiceImpl alertService;

    private Account testAccount;
    private Instrument testInstrument;
    private PriceAlert testAlert;

    @BeforeEach
    void setUp() {
        // Create service with a limit of 5 for testing
        alertService = new AlertServiceImpl(
                priceAlertDataMapper,
                accountDataMapper,
                instrumentDataMapper,
                5  // maxActiveAlertsPerAccount for testing
        );

        testAccount = new Account();
        testAccount.setAccountId(1001L);
        testAccount.setAccountReference("ACC-001");
        testAccount.setStatus(AccountStatus.ACTIVE);

        testInstrument = new Instrument();
        testInstrument.setSymbol("AAPL");
        testInstrument.setTradable(true);

        testAlert = new PriceAlert();
        testAlert.setId(1L);
        testAlert.setAccountId(1001L);
        testAlert.setSymbol("AAPL");
        testAlert.setDirection(AlertDirection.BELOW);
        testAlert.setThreshold(new BigDecimal("150.00"));
        testAlert.setStatus(AlertStatus.ACTIVE);
        testAlert.setCreatedOn(OffsetDateTime.now());
        testAlert.setUpdatedOn(OffsetDateTime.now());
    }

    @Test
    void createAlert_validRequest_success() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(0);
        when(instrumentDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(testInstrument);
        when(priceAlertDataMapper.insertPriceAlert(any())).thenReturn(1);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, any()))
                .thenReturn(testAlert);

        AlertResponse response = alertService.createAlert(1001L, request);

        assertEquals("AAPL", response.getSymbol());
        assertEquals("BELOW", response.getDirection());
        assertEquals(new BigDecimal("150.00"), response.getThreshold());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void createAlert_instrumentNotFound_throwsException() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("INVALID");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(0);
        when(instrumentDataMapper.findInstrumentBySymbol("INVALID")).thenReturn(null);

        assertThrows(
                InstrumentNotFoundException.class,
                () -> alertService.createAlert(1001L, request)
        );
    }

    @Test
    void createAlert_accountNotFound_throwsException() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(null);

        assertThrows(
                AccountNotFoundException.class,
                () -> alertService.createAlert(1001L, request)
        );
    }

    @Test
    void createAlert_accountNotActive_throwsException() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        Account inactiveAccount = new Account();
        inactiveAccount.setAccountId(1001L);
        inactiveAccount.setStatus(AccountStatus.SUSPENDED);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(inactiveAccount);

        assertThrows(
                AccountNotActiveException.class,
                () -> alertService.createAlert(1001L, request)
        );
    }

    @Test
    void createAlert_invalidDirection_throwsException() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("INVALID");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(instrumentDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(testInstrument);

        assertThrows(
                AlertStateException.class,
                () -> alertService.createAlert(1001L, request)
        );
    }

    @Test
    void createAlert_limitNotExceeded_success() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(2);
        when(instrumentDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(testInstrument);
        when(priceAlertDataMapper.insertPriceAlert(any())).thenReturn(1);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, any()))
                .thenReturn(testAlert);

        AlertResponse response = alertService.createAlert(1001L, request);

        assertEquals("AAPL", response.getSymbol());
    }

    @Test
    void createAlert_limitExceeded_throwsException() {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(5);

        assertThrows(
                AlertLimitExceededException.class,
                () -> alertService.createAlert(1001L, request)
        );
    }

    @Test
    void createAlert_atLimitBoundary_throwsException() {
        // When exactly at the limit (5), should not allow more
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(5);

        assertThrows(
                AlertLimitExceededException.class,
                () -> alertService.createAlert(1001L, request)
        );
    }

    @Test
    void createAlert_justBelowLimit_success() {
        // When at 4 active alerts (limit is 5), should allow one more
        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(4);
        when(instrumentDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(testInstrument);
        when(priceAlertDataMapper.insertPriceAlert(any())).thenReturn(1);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, any()))
                .thenReturn(testAlert);

        AlertResponse response = alertService.createAlert(1001L, request);

        assertEquals("AAPL", response.getSymbol());
    }

    @Test
    void createAlert_cancelledAlertsNotCountedAgainstLimit() {
        // Create a service with low limit to test this scenario
        AlertServiceImpl limitedService = new AlertServiceImpl(
                priceAlertDataMapper,
                accountDataMapper,
                instrumentDataMapper,
                2  // Very low limit for this test
        );

        CreateAlertRequest request = new CreateAlertRequest();
        request.setSymbol("AAPL");
        request.setDirection("BELOW");
        request.setThreshold(new BigDecimal("150.00"));

        // Account has 2 active alerts, but also has cancelled ones
        // Only active alerts should count
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.countActiveAlertsByAccountId(1001L)).thenReturn(1);  // Only 1 ACTIVE
        when(instrumentDataMapper.findInstrumentBySymbol("AAPL")).thenReturn(testInstrument);
        when(priceAlertDataMapper.insertPriceAlert(any())).thenReturn(1);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, any()))
                .thenReturn(testAlert);

        AlertResponse response = limitedService.createAlert(1001L, request);

        assertEquals("AAPL", response.getSymbol());
    }

    @Test
    void listAlerts_noFilter_returnsAllAlerts() {
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertsByAccountId(1001L, null))
                .thenReturn(List.of(testAlert));

        List<AlertResponse> responses = alertService.listAlerts(1001L, null);

        assertEquals(1, responses.size());
        assertEquals("AAPL", responses.get(0).getSymbol());
    }

    @Test
    void listAlerts_withStatusFilter_returnsFilteredAlerts() {
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertsByAccountId(1001L, "TRIGGERED"))
                .thenReturn(List.of(testAlert));

        List<AlertResponse> responses = alertService.listAlerts(1001L, "TRIGGERED");

        assertEquals(1, responses.size());
    }

    @Test
    void getAlert_validId_returnsAlert() {
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, 1L))
                .thenReturn(testAlert);

        AlertResponse response = alertService.getAlert(1001L, 1L);

        assertEquals(1L, response.getId());
        assertEquals("AAPL", response.getSymbol());
    }

    @Test
    void getAlert_invalidId_throwsException() {
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, 999L))
                .thenReturn(null);

        assertThrows(
                AlertNotFoundException.class,
                () -> alertService.getAlert(1001L, 999L)
        );
    }

    @Test
    void cancelAlert_activeAlert_success() {
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, 1L))
                .thenReturn(testAlert);

        alertService.cancelAlert(1001L, 1L);

        verify(priceAlertDataMapper).updateAlertStatus(1001L, 1L, "CANCELLED", null);
    }

    @Test
    void cancelAlert_alreadyCancelled_throwsException() {
        testAlert.setStatus(AlertStatus.CANCELLED);

        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, 1L))
                .thenReturn(testAlert);

        assertThrows(
                AlertStateException.class,
                () -> alertService.cancelAlert(1001L, 1L)
        );
    }

    @Test
    void cancelAlert_alertNotFound_throwsException() {
        when(accountDataMapper.findAccountById(1001L)).thenReturn(testAccount);
        when(priceAlertDataMapper.findAlertByAccountIdAndId(1001L, 999L))
                .thenReturn(null);

        assertThrows(
                AlertNotFoundException.class,
                () -> alertService.cancelAlert(1001L, 999L)
        );
    }
}

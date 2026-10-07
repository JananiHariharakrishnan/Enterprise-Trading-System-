package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.request.CreateAlertRequest;
import com.tradingsystem.dto.response.AlertResponse;
import com.tradingsystem.enums.AlertDirection;
import com.tradingsystem.enums.AlertStatus;
import com.tradingsystem.exception.AccountNotActiveException;
import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.exception.AlertLimitExceededException;
import com.tradingsystem.exception.AlertNotFoundException;
import com.tradingsystem.exception.AlertStateException;
import com.tradingsystem.exception.InstrumentNotFoundException;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.InstrumentDataMapper;
import com.tradingsystem.mapper.PriceAlertDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.Instrument;
import com.tradingsystem.model.PriceAlert;
import com.tradingsystem.service.AlertService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertServiceImpl implements AlertService {

    private final PriceAlertDataMapper priceAlertDataMapper;
    private final AccountDataMapper accountDataMapper;
    private final InstrumentDataMapper instrumentDataMapper;
    private final int maxActiveAlertsPerAccount;

    public AlertServiceImpl(
            PriceAlertDataMapper priceAlertDataMapper,
            AccountDataMapper accountDataMapper,
            InstrumentDataMapper instrumentDataMapper,
            @Value("${app.alerts.max-active-per-account:50}") int maxActiveAlertsPerAccount
    ) {
        this.priceAlertDataMapper = priceAlertDataMapper;
        this.accountDataMapper = accountDataMapper;
        this.instrumentDataMapper = instrumentDataMapper;
        this.maxActiveAlertsPerAccount = maxActiveAlertsPerAccount;
    }

    @Override
    @Transactional
    public AlertResponse createAlert(
            Long accountId,
            CreateAlertRequest request
    ) {
        Account account = getAuthorizedAccount(accountId);

        // Check active alert limit
        int activeAlertCount = priceAlertDataMapper.countActiveAlertsByAccountId(account.getAccountId());
        if (activeAlertCount >= maxActiveAlertsPerAccount) {
            throw new AlertLimitExceededException(maxActiveAlertsPerAccount);
        }

        // Validate instrument exists
        Instrument instrument = instrumentDataMapper.findInstrumentBySymbol(request.getSymbol());
        if (instrument == null) {
            throw new InstrumentNotFoundException();
        }

        // Validate direction
        AlertDirection direction;
        try {
            direction = AlertDirection.valueOf(request.getDirection());
        } catch (IllegalArgumentException e) {
            throw new AlertStateException("Invalid direction: must be ABOVE or BELOW");
        }

        // Create the alert
        PriceAlert alert = new PriceAlert();
        alert.setAccountId(account.getAccountId());
        alert.setSymbol(request.getSymbol());
        alert.setDirection(direction);
        alert.setThreshold(request.getThreshold());
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedOn(OffsetDateTime.now());
        alert.setUpdatedOn(OffsetDateTime.now());

        priceAlertDataMapper.insertPriceAlert(alert);

        PriceAlert persisted = priceAlertDataMapper.findAlertByAccountIdAndId(
                account.getAccountId(),
                alert.getId()
        );

        if (persisted == null) {
            throw new IllegalStateException("Created alert could not be reloaded");
        }

        return toResponse(persisted);
    }

    @Override
    public List<AlertResponse> listAlerts(
            Long accountId,
            String status
    ) {
        Account account = getAuthorizedAccount(accountId);

        List<PriceAlert> alerts = priceAlertDataMapper.findAlertsByAccountId(
                account.getAccountId(),
                status
        );

        List<AlertResponse> responses = new ArrayList<>();
        for (PriceAlert alert : alerts) {
            responses.add(toResponse(alert));
        }

        return responses;
    }

    @Override
    public AlertResponse getAlert(
            Long accountId,
            Long alertId
    ) {
        Account account = getAuthorizedAccount(accountId);

        PriceAlert alert = priceAlertDataMapper.findAlertByAccountIdAndId(
                account.getAccountId(),
                alertId
        );

        if (alert == null) {
            throw new AlertNotFoundException();
        }

        return toResponse(alert);
    }

    @Override
    @Transactional
    public void cancelAlert(
            Long accountId,
            Long alertId
    ) {
        Account account = getAuthorizedAccount(accountId);

        PriceAlert alert = priceAlertDataMapper.findAlertByAccountIdAndId(
                account.getAccountId(),
                alertId
        );

        if (alert == null) {
            throw new AlertNotFoundException();
        }

        // Only ACTIVE or TRIGGERED alerts can be cancelled
        if (alert.getStatus() == AlertStatus.CANCELLED) {
            throw new AlertStateException("Alert is already cancelled");
        }

        // Update status to CANCELLED
        priceAlertDataMapper.updateAlertStatus(
                account.getAccountId(),
                alertId,
                "CANCELLED",
                null
        );
    }

    /**
     * Convert PriceAlert model to AlertResponse DTO.
     */
    private AlertResponse toResponse(PriceAlert alert) {
        AlertResponse response = new AlertResponse();
        response.setId(alert.getId());
        response.setSymbol(alert.getSymbol());
        response.setDirection(alert.getDirection().toString());
        response.setThreshold(alert.getThreshold());
        response.setStatus(alert.getStatus().toString());
        response.setTriggeredOn(alert.getTriggeredOn());
        response.setCreatedOn(alert.getCreatedOn());
        response.setUpdatedOn(alert.getUpdatedOn());
        return response;
    }

    /**
     * Verify account exists, is active, and belongs to the authenticated user.
     */
    private Account getAuthorizedAccount(Long accountId) {
        Account account = accountDataMapper.findAccountById(accountId);

        if (account == null) {
            throw new AccountNotFoundException();
        }

        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }

        return account;
    }
}

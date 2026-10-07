package com.tradingsystem.service;

import com.tradingsystem.dto.request.CreateAlertRequest;
import com.tradingsystem.dto.response.AlertResponse;

import java.util.List;

public interface AlertService {

    /**
     * Create a new price alert for the account.
     * Account ID is enforced from JWT, never accepted from client.
     */
    AlertResponse createAlert(
            Long accountId,
            CreateAlertRequest request
    );

    /**
     * List all alerts for the account, optionally filtered by status.
     */
    List<AlertResponse> listAlerts(
            Long accountId,
            String status
    );

    /**
     * Get a specific alert by ID, ensuring the account owns it.
     */
    AlertResponse getAlert(
            Long accountId,
            Long alertId
    );

    /**
     * Cancel an alert (mark it as CANCELLED).
     * Can only cancel ACTIVE or TRIGGERED alerts.
     */
    void cancelAlert(
            Long accountId,
            Long alertId
    );
}

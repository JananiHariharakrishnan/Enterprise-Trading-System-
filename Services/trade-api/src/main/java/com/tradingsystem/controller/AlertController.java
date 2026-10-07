package com.tradingsystem.controller;

import com.tradingsystem.dto.request.CreateAlertRequest;
import com.tradingsystem.dto.response.AlertResponse;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;
    private final CurrentUser currentUser;

    public AlertController(AlertService alertService, CurrentUser currentUser) {
        this.alertService = alertService;
        this.currentUser = currentUser;
    }

    /**
     * Create a new price alert.
     * Account ID is derived from the authenticated JWT.
     */
    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(
            @Valid @RequestBody CreateAlertRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(alertService.createAlert(currentUser.getAccountId(), request));
    }

    /**
     * List all alerts for the authenticated account.
     * Optional status filter: ACTIVE, TRIGGERED, or CANCELLED.
     */
    @GetMapping
    public ResponseEntity<List<AlertResponse>> listAlerts(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(
                alertService.listAlerts(currentUser.getAccountId(), status)
        );
    }

    /**
     * Get a specific alert by ID.
     */
    @GetMapping("/{alertId}")
    public ResponseEntity<AlertResponse> getAlert(
            @PathVariable Long alertId
    ) {
        return ResponseEntity.ok(
                alertService.getAlert(currentUser.getAccountId(), alertId)
        );
    }

    /**
     * Cancel an alert (mark as CANCELLED).
     * Can only cancel ACTIVE or TRIGGERED alerts.
     */
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> cancelAlert(
            @PathVariable Long alertId
    ) {
        alertService.cancelAlert(currentUser.getAccountId(), alertId);
        return ResponseEntity.noContent().build();
    }
}

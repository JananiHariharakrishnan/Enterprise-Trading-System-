package com.tradingsystem.service;

import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Service responsible for sending notifications to users.
 */
public interface NotificationService {

    /**
     * Sends an email notification to the user when an order is filled.
     *
     * @param user the user details retrieved from the database
     * @param payload the trade event details containing execution information
     */
    void sendOrderFilledNotification(User user, TradeEventPayload payload);

    /**
     * Sends an email notification to the user when a price alert is triggered.
     *
     * The notification service internally handles:
     * - Reading customer email and preferences
     * - Determining delivery channel
     * - Building and sending the message
     *
     * The caller (Alert module) must NOT:
     * - Read customer preferences
     * - Access notification tables
     * - Decide the channel
     * - Call sendEmail() directly
     *
     * @param user the user who created the alert
     * @param symbol the instrument symbol (e.g., "AAPL")
     * @param direction the alert direction: "ABOVE" or "BELOW"
     * @param threshold the price threshold the customer specified
     * @param currentPrice the actual price that triggered the alert
     * @param triggeredOn the timestamp when the alert was triggered
     */
    void sendPriceAlertTriggeredNotification(
            User user,
            String symbol,
            String direction,
            BigDecimal threshold,
            BigDecimal currentPrice,
            OffsetDateTime triggeredOn
    );

    /**
     * Sends a plain email message directly to a specified recipient.
     *
     * @param to recipient email address
     * @param subject email subject
     * @param body email body text
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendEmail(String to, String subject, String body);
}

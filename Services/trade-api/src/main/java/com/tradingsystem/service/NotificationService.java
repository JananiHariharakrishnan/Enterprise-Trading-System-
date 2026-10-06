package com.tradingsystem.service;

import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.User;

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
     * Sends a plain email message directly to a specified recipient.
     *
     * @param to recipient email address
     * @param subject email subject
     * @param body email body text
     * @return true if email was sent successfully, false otherwise
     */
    boolean sendEmail(String to, String subject, String body);
}

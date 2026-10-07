package com.tradingsystem.service.Impl;

import com.tradingsystem.mapper.CustomerPreferencesDataMapper;
import com.tradingsystem.mapper.UserNotificationDataMapper;
import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.CustomerPreferences;
import com.tradingsystem.model.User;
import com.tradingsystem.model.UserNotification;
import com.tradingsystem.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final JavaMailSender mailSender;
    private final UserNotificationDataMapper notificationDataMapper;
    private final CustomerPreferencesDataMapper preferencesDataMapper;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.notification.mail.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${app.notification.mail.enabled:true}")
    private boolean mailEnabled;

    public NotificationServiceImpl(
            @Autowired(required = false) JavaMailSender mailSender,
            UserNotificationDataMapper notificationDataMapper,
            CustomerPreferencesDataMapper preferencesDataMapper
    ) {
        this.mailSender = mailSender;
        this.notificationDataMapper = notificationDataMapper;
        this.preferencesDataMapper = preferencesDataMapper;
    }

    @Override
    public void sendOrderFilledNotification(User user, TradeEventPayload payload) {
        if (user == null) {
            log.warn("Cannot send notification: user is null for trade event orderId={}",
                    payload != null ? payload.getOrderId() : "unknown");
            return;
        }

        String recipientName = displayName(user);
        String symbol = payload != null && payload.getSymbol() != null ? payload.getSymbol() : "N/A";
        String side = payload != null && payload.getSide() != null ? payload.getSide() : "TRADE";
        String quantity = payload != null && payload.getQuantity() != null ? payload.getQuantity().toPlainString() : "N/A";
        String executedPrice = payload != null && payload.getExecutedPrice() != null
                ? "$" + payload.getExecutedPrice().toPlainString()
                : "N/A";
        String subject = String.format("Order Filled: %s %s - Trade Confirmation", side, symbol);
        String emailBody = buildEmailBody(recipientName, payload);
        String inAppBody = String.format("%s %s %s was filled at %s.", side, quantity, symbol, executedPrice);

        boolean emailed = deliverEmailIfOptedIn(user, subject, emailBody);
        saveInAppNotification(user, "ORDER_FILLED", "Order filled", inAppBody, symbol, emailed);
    }

    @Override
    public void sendPriceAlertTriggeredNotification(
            User user,
            String symbol,
            String direction,
            BigDecimal threshold,
            BigDecimal currentPrice,
            OffsetDateTime triggeredOn
    ) {
        if (user == null) {
            log.warn("Cannot send notification: user is null for price alert on symbol={}", symbol != null ? symbol : "unknown");
            return;
        }

        String recipientName = displayName(user);
        String safeSymbol = symbol != null ? symbol : "N/A";
        String safeDirection = direction != null ? direction : "UNKNOWN";
        String thresholdStr = threshold != null ? "$" + threshold.toPlainString() : "N/A";
        String currentPriceStr = currentPrice != null ? "$" + currentPrice.toPlainString() : "N/A";
        String subject = String.format("Price Alert Triggered: %s %s", safeSymbol, safeDirection);
        String emailBody = buildPriceAlertEmailBody(recipientName, safeSymbol, safeDirection, threshold, currentPrice, triggeredOn);
        String inAppBody = String.format("%s moved %s %s. Current price %s.",
                safeSymbol, safeDirection.toLowerCase(), thresholdStr, currentPriceStr);

        boolean emailed = deliverEmailIfOptedIn(user, subject, emailBody);
        saveInAppNotification(user, "PRICE_ALERT", "Price alert", inAppBody, safeSymbol, emailed);
    }

    private String displayName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "Customer";
    }

    /**
     * Email is off unless mail is configured, the account opted in, and a profile email exists.
     */
    private boolean deliverEmailIfOptedIn(User user, String subject, String body) {
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.notification.mail.enabled=false).");
            return false;
        }

        if (!isEmailOptedIn(user.getAccountId())) {
            log.info("Email notification skipped: account {} has not enabled email notifications.", user.getAccountId());
            return false;
        }

        String recipientEmail = user.getEmail();
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            log.warn("Cannot send notification: User (username={}, accountId={}) has no email address configured.",
                    user.getUsername(), user.getAccountId());
            return false;
        }

        return sendEmail(recipientEmail.trim(), subject, body);
    }

    private boolean isEmailOptedIn(Long accountId) {
        if (preferencesDataMapper == null || accountId == null) {
            return false;
        }
        try {
            CustomerPreferences prefs = preferencesDataMapper.findByAccountId(accountId);
            return prefs != null && prefs.isEmailNotificationsEnabled();
        } catch (Exception ex) {
            log.warn("Unable to read email notification preference for accountId={}: {}", accountId, ex.getMessage());
            return false;
        }
    }

    private String usableSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || "N/A".equalsIgnoreCase(symbol.trim())) {
            return null;
        }
        return symbol.trim();
    }

    private void saveInAppNotification(User user, String category, String title, String body, String symbol, boolean emailSent) {
        if (notificationDataMapper == null || user == null || user.getAccountId() == null) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setAccountId(user.getAccountId());
        notification.setUserId(user.getId());
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setSymbol(usableSymbol(symbol));
        notification.setEmailSent(emailSent);
        try {
            notificationDataMapper.insert(notification);
        } catch (Exception ex) {
            log.error("Failed to save in-app notification for accountId={}: {}", user.getAccountId(), ex.getMessage(), ex);
        }
    }

    @Override
    public boolean sendEmail(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("Email notification skipped: mail is disabled.");
            return false;
        }

        if (mailSender == null) {
            log.warn("JavaMailSender bean is not available. Skipping email delivery to {}.", to);
            return false;
        }

        if (mailUsername == null || mailUsername.trim().isEmpty()) {
            log.info("Email notification skipped: MAIL_USERNAME is not set in environment.");
            return false;
        }

        if (to == null || to.trim().isEmpty()) {
            log.warn("Email recipient address is missing.");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            } else if (mailUsername != null && !mailUsername.isBlank()) {
                message.setFrom(mailUsername);
            }

            message.setTo(to.trim());
            message.setSubject(subject);
            message.setText(body);

            log.info("Sending trade notification email to {} (Subject: '{}')", to, subject);
            mailSender.send(message);
            log.info("Email successfully sent to {}", to);
            return true;

        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            return false;
        }
    }

    private String buildEmailBody(String recipientName, TradeEventPayload payload) {
        String orderId = payload != null && payload.getOrderId() != null ? payload.getOrderId() : "N/A";
        String symbol = payload != null && payload.getSymbol() != null ? payload.getSymbol() : "N/A";
        String side = payload != null && payload.getSide() != null ? payload.getSide() : "N/A";
        String quantity = payload != null && payload.getQuantity() != null ? payload.getQuantity().toPlainString() : "N/A";
        String executedPrice = payload != null && payload.getExecutedPrice() != null ? "$" + payload.getExecutedPrice().toPlainString() : "N/A";
        String limitPrice = payload != null && payload.getPrice() != null ? "$" + payload.getPrice().toPlainString() : "N/A";
        String executedOn = payload != null && payload.getExecutedOn() != null ? payload.getExecutedOn() : "N/A";

        return String.format(
                "Hello %s,\n\n" +
                "Good news! Your trade order has been successfully FILLED.\n\n" +
                "========================================\n" +
                "           ORDER EXECUTION SUMMARY       \n" +
                "========================================\n" +
                "Order ID:        %s\n" +
                "Symbol:          %s\n" +
                "Side:            %s\n" +
                "Quantity:        %s\n" +
                "Executed Price:  %s\n" +
                "Limit Price:     %s\n" +
                "Status:          FILLED\n" +
                "Execution Time:  %s\n" +
                "========================================\n\n" +
                "You can view your updated portfolio and cash balance in your account.\n\n" +
                "Thank you for trading with us!\n\n" +
                "Best regards,\n" +
                "Trading System Team\n",
                recipientName,
                orderId,
                symbol,
                side,
                quantity,
                executedPrice,
                limitPrice,
                executedOn
        );
    }

    private String buildPriceAlertEmailBody(
            String recipientName,
            String symbol,
            String direction,
            BigDecimal threshold,
            BigDecimal currentPrice,
            OffsetDateTime triggeredOn
    ) {
        String thresholdStr = threshold != null ? "$" + threshold.toPlainString() : "N/A";
        String currentPriceStr = currentPrice != null ? "$" + currentPrice.toPlainString() : "N/A";
        String triggeredOnStr = triggeredOn != null ? triggeredOn.toString() : "N/A";

        return String.format(
                "Hello %s,\n\n" +
                "Your price alert has been triggered!\n\n" +
                "========================================\n" +
                "           PRICE ALERT SUMMARY          \n" +
                "========================================\n" +
                "Symbol:          %s\n" +
                "Alert Type:      %s\n" +
                "Alert Price:     %s\n" +
                "Current Price:   %s\n" +
                "Status:          TRIGGERED\n" +
                "Triggered At:    %s\n" +
                "========================================\n\n" +
                "You can manage your price alerts in your account dashboard.\n\n" +
                "Best regards,\n" +
                "Trading System Team\n",
                recipientName,
                symbol,
                direction,
                thresholdStr,
                currentPriceStr,
                triggeredOnStr
        );
    }
}

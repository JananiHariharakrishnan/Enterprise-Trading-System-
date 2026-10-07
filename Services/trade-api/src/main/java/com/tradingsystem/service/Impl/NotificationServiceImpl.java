package com.tradingsystem.service.Impl;

import com.tradingsystem.mapper.CustomerPreferencesDataMapper;
import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.CustomerPreferences;
import com.tradingsystem.model.User;
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
    private CustomerPreferencesDataMapper customerPreferencesDataMapper;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.notification.mail.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${app.notification.mail.enabled:true}")
    private boolean mailEnabled;

    public NotificationServiceImpl(@Autowired(required = false) JavaMailSender mailSender) {
        this(mailSender, null);
    }

    @Autowired
    public NotificationServiceImpl(
            @Autowired(required = false) JavaMailSender mailSender,
            @Autowired(required = false) CustomerPreferencesDataMapper customerPreferencesDataMapper
    ) {
        this.mailSender = mailSender;
        this.customerPreferencesDataMapper = customerPreferencesDataMapper;
    }

    public void setCustomerPreferencesDataMapper(CustomerPreferencesDataMapper customerPreferencesDataMapper) {
        this.customerPreferencesDataMapper = customerPreferencesDataMapper;
    }

    private boolean isEmailEnabled(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return false;
        }
        for (String ch : channels) {
            if (ch == null) continue;
            String trimmed = ch.trim().toUpperCase();
            if ("NO NOTIFICATION".equals(trimmed) || "NO_NOTIFICATION".equals(trimmed)) {
                return false;
            }
        }
        for (String ch : channels) {
            if (ch == null) continue;
            String trimmed = ch.trim().toUpperCase();
            if ("EMAIL".equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void sendOrderFilledNotification(User user, TradeEventPayload payload) {
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.notification.mail.enabled=false).");
            return;
        }

        if (user == null) {
            log.warn("Cannot send email: user is null for trade event orderId={}",
                    payload != null ? payload.getOrderId() : "unknown");
            return;
        }

        Long accountId = user.getAccountId() != null ? user.getAccountId() : (payload != null ? payload.getAccountId() : null);
        if (customerPreferencesDataMapper != null && accountId != null) {
            try {
                CustomerPreferences prefs = customerPreferencesDataMapper.findByAccountId(accountId);
                if (prefs != null) {
                    List<String> channels = prefs.getNotificationsTradeFills();
                    if (!isEmailEnabled(channels)) {
                        log.info("Order filled email notification skipped: notifications disabled in preferences for accountId={}", accountId);
                        return;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to check customer preferences for accountId={}: {}", accountId, ex.getMessage(), ex);
            }
        }

        String recipientEmail = user.getEmail();
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            log.warn("Cannot send notification: User (username={}, accountId={}) has no email address configured.",
                    user.getUsername(), user.getAccountId());
            return;
        }

        recipientEmail = recipientEmail.trim();

        String recipientName = user.getFirstName();
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername()
                    : "Customer";
        }

        String symbol = payload != null && payload.getSymbol() != null ? payload.getSymbol() : "N/A";
        String side = payload != null && payload.getSide() != null ? payload.getSide() : "TRADE";
        String subject = String.format("Order Filled: %s %s - Trade Confirmation", side, symbol);
        String body = buildEmailBody(recipientName, payload);

        sendEmail(recipientEmail, subject, body);
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
        if (!mailEnabled) {
            log.info("Email notifications are disabled (app.notification.mail.enabled=false).");
            return;
        }

        if (user == null) {
            log.warn("Cannot send email: user is null for price alert on symbol={}", symbol != null ? symbol : "unknown");
            return;
        }

        if (customerPreferencesDataMapper != null && user.getAccountId() != null) {
            try {
                CustomerPreferences prefs = customerPreferencesDataMapper.findByAccountId(user.getAccountId());
                if (prefs != null) {
                    List<String> channels = prefs.getNotificationsPriceAlerts();
                    if (!isEmailEnabled(channels)) {
                        log.info("Price alert email notification skipped: alerts disabled in preferences for accountId={}", user.getAccountId());
                        return;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to check customer preferences for accountId={}: {}", user.getAccountId(), ex.getMessage(), ex);
            }
        }

        String recipientEmail = user.getEmail();
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            log.warn("Cannot send notification: User (username={}, accountId={}) has no email address configured.",
                    user.getUsername(), user.getAccountId());
            return;
        }

        recipientEmail = recipientEmail.trim();

        String recipientName = user.getFirstName();
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = user.getUsername() != null && !user.getUsername().isBlank()
                    ? user.getUsername()
                    : "Customer";
        }

        String safeSymbol = symbol != null ? symbol : "N/A";
        String safeDirection = direction != null ? direction : "UNKNOWN";
        String subject = String.format("Price Alert Triggered: %s %s", safeSymbol, safeDirection);
        String body = buildPriceAlertEmailBody(recipientName, safeSymbol, safeDirection, threshold, currentPrice, triggeredOn);

        sendEmail(recipientEmail, subject, body);
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

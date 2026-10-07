package com.tradingsystem.controller;

import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.User;
import com.tradingsystem.service.NotificationService;
import com.tradingsystem.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for testing and verifying notification and email delivery.
 * Accessible under /internal/notifications/**.
 */
@RestController
@RequestMapping("/internal/notifications")
public class NotificationTestController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationTestController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    /**
     * Send a simple test email to verify SMTP credentials and network connectivity.
     */
    @PostMapping("/test-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestParam("to") String to) {
        boolean sent = notificationService.sendEmail(
                to,
                "Trading Platform Test Email",
                "Hello,\n\nThis is a test notification from the Trading Platform. Your email configuration is working correctly!\n\nBest regards,\nTrading System Team"
        );

        if (sent) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Test email sent successfully to " + to
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "FAILED",
                    "message", "Failed to send test email. Check server logs and SMTP configuration in .env"
            ));
        }
    }

    /**
     * Trigger a mock order-filled notification for the user associated with accountId.
     */
    @PostMapping("/simulate-fill/{accountId}")
    public ResponseEntity<Map<String, Object>> simulateFilledEvent(
            @PathVariable("accountId") Long accountId,
            @RequestParam(value = "symbol", defaultValue = "AAPL") String symbol,
            @RequestParam(value = "quantity", defaultValue = "10") Integer quantity,
            @RequestParam(value = "price", defaultValue = "150.00") BigDecimal price
    ) {
        User user = userService.getUserByAccountId(accountId);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "No user found for accountId=" + accountId
            ));
        }

        TradeEventPayload payload = new TradeEventPayload();
        payload.setOrderId(UUID.randomUUID().toString());
        payload.setAccountId(accountId);
        payload.setSymbol(symbol);
        payload.setSide("BUY");
        payload.setQuantity(BigDecimal.valueOf(quantity));
        payload.setPrice(price);
        payload.setExecutedPrice(price);
        payload.setStatus("FILLED");
        payload.setExecutedOn(OffsetDateTime.now(ZoneOffset.UTC).toString());

        notificationService.sendOrderFilledNotification(user, payload);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Order filled notification processed for user " + user.getUsername(),
                "recipientEmail", user.getEmail() != null ? user.getEmail() : "NO EMAIL IN DB"
        ));
    }
}

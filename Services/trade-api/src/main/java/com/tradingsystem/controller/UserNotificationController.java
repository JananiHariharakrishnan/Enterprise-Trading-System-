package com.tradingsystem.controller;

import com.tradingsystem.model.UserNotification;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.UserNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class UserNotificationController {

    private final UserNotificationService notificationService;
    private final CurrentUser currentUser;

    public UserNotificationController(UserNotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<List<UserNotification>> listNotifications() {
        return ResponseEntity.ok(notificationService.listForAccount(currentUser.getAccountId()));
    }

    @PostMapping
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead(currentUser.getAccountId());
        return ResponseEntity.noContent().build();
    }
}

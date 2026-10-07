package com.tradingsystem.controller;

import com.tradingsystem.model.User;
import com.tradingsystem.service.NotificationService;
import com.tradingsystem.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationTestControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    private NotificationTestController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationTestController(notificationService, userService);
    }

    @Test
    void sendTestEmail_success() {
        when(notificationService.sendEmail(eq("test@example.com"), anyString(), anyString())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail("test@example.com");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("SUCCESS", response.getBody().get("status"));
    }

    @Test
    void sendTestEmail_failure() {
        when(notificationService.sendEmail(eq("test@example.com"), anyString(), anyString())).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail("test@example.com");

        assertEquals(500, response.getStatusCode().value());
        assertEquals("FAILED", response.getBody().get("status"));
    }

    @Test
    void simulateFilledEvent_userFound() {
        User user = new User();
        user.setUsername("trader1");
        user.setEmail("trader@example.com");
        when(userService.getUserByAccountId(1L)).thenReturn(user);

        ResponseEntity<Map<String, Object>> response = controller.simulateFilledEvent(
                1L, "AAPL", 10, new BigDecimal("150.00")
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("trader@example.com", response.getBody().get("recipientEmail"));
        verify(notificationService, times(1)).sendOrderFilledNotification(eq(user), any());
    }

    @Test
    void simulateFilledEvent_userNotFound() {
        when(userService.getUserByAccountId(999L)).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.simulateFilledEvent(
                999L, "AAPL", 10, new BigDecimal("150.00")
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("ERROR", response.getBody().get("status"));
        verify(notificationService, never()).sendOrderFilledNotification(any(), any());
    }
}

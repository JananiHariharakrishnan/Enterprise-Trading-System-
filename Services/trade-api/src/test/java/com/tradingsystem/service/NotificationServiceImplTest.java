package com.tradingsystem.service;

import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.User;
import com.tradingsystem.service.Impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(mailSender);
        ReflectionTestUtils.setField(notificationService, "mailUsername", "sender@tradingsystem.com");
        ReflectionTestUtils.setField(notificationService, "fromEmail", "sender@tradingsystem.com");
        ReflectionTestUtils.setField(notificationService, "mailEnabled", true);
    }

    @Test
    void sendOrderFilledNotification_success() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAccountId(1L);
        user.setUsername("trader1");
        user.setFirstName("Alice");
        user.setEmail("alice@example.com");

        TradeEventPayload payload = new TradeEventPayload();
        payload.setOrderId("order-uuid-1");
        payload.setAccountId(1L);
        payload.setSymbol("AAPL");
        payload.setSide("BUY");
        payload.setQuantity(new BigDecimal("10"));
        payload.setPrice(new BigDecimal("150.00"));
        payload.setExecutedPrice(new BigDecimal("149.50"));
        payload.setStatus("FILLED");
        payload.setExecutedOn("2026-10-06T15:00:00Z");

        notificationService.sendOrderFilledNotification(user, payload);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("alice@example.com", sent.getTo()[0]);
        assertTrue(sent.getSubject().contains("AAPL"));
        assertTrue(sent.getSubject().contains("BUY"));
        assertNotNull(sent.getText());
        assertTrue(sent.getText().contains("Alice"));
        assertTrue(sent.getText().contains("FILLED"));
        assertTrue(sent.getText().contains("149.50"));
    }

    @Test
    void sendOrderFilledNotification_skippedWhenMailDisabled() {
        ReflectionTestUtils.setField(notificationService, "mailEnabled", false);

        User user = new User();
        user.setEmail("alice@example.com");
        TradeEventPayload payload = new TradeEventPayload();

        notificationService.sendOrderFilledNotification(user, payload);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendOrderFilledNotification_skippedWhenUserEmailMissing() {
        User user = new User();
        user.setEmail(null);
        TradeEventPayload payload = new TradeEventPayload();

        notificationService.sendOrderFilledNotification(user, payload);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendOrderFilledNotification_skippedWhenMailUsernameNotSet() {
        ReflectionTestUtils.setField(notificationService, "mailUsername", "");

        User user = new User();
        user.setEmail("alice@example.com");
        TradeEventPayload payload = new TradeEventPayload();

        notificationService.sendOrderFilledNotification(user, payload);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendOrderFilledNotification_handlesMailExceptionGracefully() {
        User user = new User();
        user.setEmail("alice@example.com");
        TradeEventPayload payload = new TradeEventPayload();
        payload.setOrderId("ord-1");

        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw
        assertDoesNotThrow(() -> notificationService.sendOrderFilledNotification(user, payload));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_directCallSuccess() {
        boolean sent = notificationService.sendEmail("bob@example.com", "Test Subject", "Test Body");

        assertTrue(sent);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}

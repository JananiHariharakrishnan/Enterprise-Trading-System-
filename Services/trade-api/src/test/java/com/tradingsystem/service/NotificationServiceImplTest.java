package com.tradingsystem.service;

import com.tradingsystem.mapper.CustomerPreferencesDataMapper;
import com.tradingsystem.messaging.TradeEventPayload;
import com.tradingsystem.model.CustomerPreferences;
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
import java.time.OffsetDateTime;
import java.util.List;
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

    // =========================================================
    // PRICE ALERT NOTIFICATION TESTS
    // =========================================================

    @Test
    void sendPriceAlertTriggeredNotification_success() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAccountId(1001L);
        user.setUsername("trader1");
        user.setFirstName("Alice");
        user.setEmail("alice@example.com");

        OffsetDateTime triggeredOn = OffsetDateTime.now();

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                triggeredOn
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("alice@example.com", sent.getTo()[0]);
        assertTrue(sent.getSubject().contains("AAPL"));
        assertTrue(sent.getSubject().contains("ABOVE"));
        assertNotNull(sent.getText());
        assertTrue(sent.getText().contains("Alice"));
        assertTrue(sent.getText().contains("TRIGGERED"));
        assertTrue(sent.getText().contains("330.00"));
        assertTrue(sent.getText().contains("335.00"));
    }

    @Test
    void sendPriceAlertTriggeredNotification_belowAlert() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAccountId(1001L);
        user.setUsername("trader2");
        user.setFirstName("Bob");
        user.setEmail("bob@example.com");

        OffsetDateTime triggeredOn = OffsetDateTime.now();

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "MSFT",
                "BELOW",
                new BigDecimal("380.00"),
                new BigDecimal("375.00"),
                triggeredOn
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("bob@example.com", sent.getTo()[0]);
        assertTrue(sent.getSubject().contains("MSFT"));
        assertTrue(sent.getSubject().contains("BELOW"));
        assertTrue(sent.getText().contains("Bob"));
        assertTrue(sent.getText().contains("TRIGGERED"));
        assertTrue(sent.getText().contains("380.00"));
        assertTrue(sent.getText().contains("375.00"));
    }

    @Test
    void sendPriceAlertTriggeredNotification_skippedWhenMailDisabled() {
        ReflectionTestUtils.setField(notificationService, "mailEnabled", false);

        User user = new User();
        user.setEmail("alice@example.com");

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPriceAlertTriggeredNotification_skippedWhenUserNull() {
        notificationService.sendPriceAlertTriggeredNotification(
                null,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPriceAlertTriggeredNotification_skippedWhenUserEmailMissing() {
        User user = new User();
        user.setEmail(null);

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPriceAlertTriggeredNotification_usesUsernameWhenFirstNameMissing() {
        User user = new User();
        user.setUsername("trader1");
        user.setFirstName(null);
        user.setEmail("trader@example.com");

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertTrue(sent.getText().contains("trader1"));
    }

    @Test
    void sendPriceAlertTriggeredNotification_handlesMailExceptionGracefully() {
        User user = new User();
        user.setEmail("alice@example.com");

        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw
        assertDoesNotThrow(() -> notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        ));

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPriceAlertTriggeredNotification_handlesMissingSymbol() {
        User user = new User();
        user.setFirstName("Alice");
        user.setEmail("alice@example.com");

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                null,
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertTrue(sent.getSubject().contains("N/A"));
        assertTrue(sent.getText().contains("N/A"));
    }

    @Test
    void sendOrderFilledNotification_skippedWhenPreferencesDisabled() {
        CustomerPreferencesDataMapper mockMapper = mock(CustomerPreferencesDataMapper.class);
        notificationService.setCustomerPreferencesDataMapper(mockMapper);

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setNotificationsTradeFills(List.of("NO NOTIFICATION"));
        when(mockMapper.findByAccountId(1L)).thenReturn(prefs);

        User user = new User();
        user.setAccountId(1L);
        user.setEmail("alice@example.com");
        TradeEventPayload payload = new TradeEventPayload();
        payload.setAccountId(1L);

        notificationService.sendOrderFilledNotification(user, payload);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendOrderFilledNotification_sentWhenPreferencesEnabled() {
        CustomerPreferencesDataMapper mockMapper = mock(CustomerPreferencesDataMapper.class);
        notificationService.setCustomerPreferencesDataMapper(mockMapper);

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setNotificationsTradeFills(List.of("IN_APP", "EMAIL"));
        when(mockMapper.findByAccountId(1L)).thenReturn(prefs);

        User user = new User();
        user.setAccountId(1L);
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");
        TradeEventPayload payload = new TradeEventPayload();
        payload.setAccountId(1L);
        payload.setOrderId("ord-1");
        payload.setSymbol("AAPL");
        payload.setSide("BUY");
        payload.setQuantity(new BigDecimal("10"));
        payload.setPrice(new BigDecimal("150.00"));
        payload.setExecutedPrice(new BigDecimal("149.50"));

        notificationService.sendOrderFilledNotification(user, payload);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPriceAlertTriggeredNotification_skippedWhenPreferencesDisabled() {
        CustomerPreferencesDataMapper mockMapper = mock(CustomerPreferencesDataMapper.class);
        notificationService.setCustomerPreferencesDataMapper(mockMapper);

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setNotificationsPriceAlerts(List.of("NO NOTIFICATION"));
        when(mockMapper.findByAccountId(1001L)).thenReturn(prefs);

        User user = new User();
        user.setAccountId(1001L);
        user.setEmail("alice@example.com");

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPriceAlertTriggeredNotification_sentWhenPreferencesEnabled() {
        CustomerPreferencesDataMapper mockMapper = mock(CustomerPreferencesDataMapper.class);
        notificationService.setCustomerPreferencesDataMapper(mockMapper);

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setNotificationsPriceAlerts(List.of("IN_APP", "EMAIL"));
        when(mockMapper.findByAccountId(1001L)).thenReturn(prefs);

        User user = new User();
        user.setAccountId(1001L);
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");

        notificationService.sendPriceAlertTriggeredNotification(
                user,
                "AAPL",
                "ABOVE",
                new BigDecimal("330.00"),
                new BigDecimal("335.00"),
                OffsetDateTime.now()
        );

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}

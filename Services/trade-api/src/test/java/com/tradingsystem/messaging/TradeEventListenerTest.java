package com.tradingsystem.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsystem.model.User;
import com.tradingsystem.service.NotificationService;
import com.tradingsystem.service.UserService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeEventListenerTest {

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private TradeEventListener tradeEventListener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tradeEventListener = new TradeEventListener(objectMapper, userService, notificationService);
    }

    @Test
    void handleTradeEvent_orderFilledEnvelope_triggersNotificationAndAcknowledges() {
        Long accountId = 10L;
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAccountId(accountId);
        user.setUsername("john_doe");
        user.setEmail("john@example.com");

        when(userService.getUserByAccountId(accountId)).thenReturn(user);

        String json = """
        {
            "eventId": "event-12345",
            "eventType": "ORDER_FILLED",
            "eventTime": "2026-10-06T15:00:00Z",
            "source": "trade-executor",
            "schemaVersion": 1,
            "payload": {
                "orderId": "order-100",
                "accountId": 10,
                "symbol": "GOOGL",
                "side": "BUY",
                "quantity": 5,
                "price": 180.00,
                "executedPrice": 179.80,
                "status": "FILLED",
                "executedOn": "2026-10-06T15:00:00Z"
            }
        }
        """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, "10", json);
        tradeEventListener.handleTradeEvent(record, acknowledgment);

        verify(userService, times(1)).getUserByAccountId(10L);

        ArgumentCaptor<TradeEventPayload> captor = ArgumentCaptor.forClass(TradeEventPayload.class);
        verify(notificationService, times(1)).sendOrderFilledNotification(eq(user), captor.capture());

        TradeEventPayload capturedPayload = captor.getValue();
        assertEquals("order-100", capturedPayload.getOrderId());
        assertEquals("GOOGL", capturedPayload.getSymbol());
        assertEquals("BUY", capturedPayload.getSide());
        assertEquals(new BigDecimal("5"), capturedPayload.getQuantity());
        assertEquals("FILLED", capturedPayload.getStatus());

        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void handleTradeEvent_rawPayloadWithStatusFilled_triggersNotification() {
        Long accountId = 5L;
        User user = new User();
        user.setAccountId(accountId);
        user.setEmail("test@example.com");

        when(userService.getUserByAccountId(accountId)).thenReturn(user);

        String json = """
        {
            "orderId": "order-200",
            "accountId": 5,
            "symbol": "TSLA",
            "side": "SELL",
            "quantity": 2,
            "price": 250.00,
            "executedPrice": 251.00,
            "status": "FILLED"
        }
        """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, "5", json);
        tradeEventListener.handleTradeEvent(record, acknowledgment);

        verify(userService, times(1)).getUserByAccountId(5L);
        verify(notificationService, times(1)).sendOrderFilledNotification(eq(user), any(TradeEventPayload.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void handleTradeEvent_orderRejected_doesNotTriggerNotification() {
        String json = """
        {
            "eventId": "event-reject",
            "eventType": "ORDER_REJECTED",
            "payload": {
                "orderId": "order-300",
                "accountId": 10,
                "symbol": "MSFT",
                "status": "REJECTED"
            }
        }
        """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, "10", json);
        tradeEventListener.handleTradeEvent(record, acknowledgment);

        verify(userService, never()).getUserByAccountId(anyLong());
        verify(notificationService, never()).sendOrderFilledNotification(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void handleTradeEvent_userNotFoundInDb_handlesGracefully() {
        Long accountId = 999L;
        when(userService.getUserByAccountId(accountId)).thenReturn(null);

        String json = """
        {
            "eventType": "ORDER_FILLED",
            "payload": {
                "orderId": "order-400",
                "accountId": 999,
                "status": "FILLED"
            }
        }
        """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, "999", json);
        tradeEventListener.handleTradeEvent(record, acknowledgment);

        verify(userService, times(1)).getUserByAccountId(999L);
        verify(notificationService, never()).sendOrderFilledNotification(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void handleTradeEvent_missingAccountId_skipsNotification() {
        String json = """
        {
            "eventType": "ORDER_FILLED",
            "payload": {
                "orderId": "order-500",
                "status": "FILLED"
            }
        }
        """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, null, json);
        tradeEventListener.handleTradeEvent(record, acknowledgment);

        verify(userService, never()).getUserByAccountId(anyLong());
        verify(notificationService, never()).sendOrderFilledNotification(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void handleTradeEvent_malformedJson_acknowledgesAndDoesNotThrow() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, "1", "NOT_JSON");
        assertDoesNotThrow(() -> tradeEventListener.handleTradeEvent(record, acknowledgment));

        verify(notificationService, never()).sendOrderFilledNotification(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void handleTradeEvent_emptyMessage_acknowledgesGracefully() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 0, "1", "");
        assertDoesNotThrow(() -> tradeEventListener.handleTradeEvent(record, acknowledgment));

        verify(notificationService, never()).sendOrderFilledNotification(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }
}

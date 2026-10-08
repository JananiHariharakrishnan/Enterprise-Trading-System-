package com.tradingsystem.controller;

import com.tradingsystem.dto.response.AccountResponse;
import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.OrderHistoryEntry;
import com.tradingsystem.dto.response.PositionResponse;
import com.tradingsystem.service.AccountService;
import com.tradingsystem.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AccountController controller;

    @Test
    void readsAccountViewsAndOrderHistory() {
        when(accountService.getAccount(7L)).thenReturn(new AccountResponse());
        when(accountService.getBalance(7L)).thenReturn(new BalanceResponse());
        when(accountService.getPositions(7L)).thenReturn(List.of(new PositionResponse()));
        OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-02-01T00:00:00Z");
        when(orderService.getOrderHistory(7L, "NEW", from, to)).thenReturn(List.of(new OrderHistoryEntry()));

        assertEquals(200, controller.getAccount(7L).getStatusCode().value());
        assertEquals(200, controller.getBalance(7L).getStatusCode().value());
        assertEquals(1, controller.getPositions(7L).getBody().size());
        assertEquals(1, controller.getOrders(7L, "NEW", from, to).getBody().size());

        verify(orderService).getOrderHistory(7L, "NEW", from, to);
    }
}

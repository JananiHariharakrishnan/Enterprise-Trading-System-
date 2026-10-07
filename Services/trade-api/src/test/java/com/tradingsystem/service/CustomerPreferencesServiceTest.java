package com.tradingsystem.service;

import com.tradingsystem.exception.AccountNotFoundException;
import com.tradingsystem.mapper.AccountDataMapper;
import com.tradingsystem.mapper.CustomerPreferencesDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.CustomerPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPreferencesServiceTest {

    @Mock
    private CustomerPreferencesDataMapper mapper;
    @Mock
    private AccountDataMapper accountDataMapper;

    @InjectMocks
    private CustomerPreferencesService service;

    @Test
    void getPreferences_returnsRowOrThrowsWhenMissing() {
        CustomerPreferences stored = new CustomerPreferences();
        stored.setAccountId(5L);
        when(mapper.findByAccountId(5L)).thenReturn(stored);

        assertSame(stored, service.getPreferences(5L));

        when(mapper.findByAccountId(6L)).thenReturn(null);
        assertThrows(AccountNotFoundException.class, () -> service.getPreferences(6L));
    }

    @Test
    void upsertPreferences_requiresAccountThenSaves() {
        CustomerPreferences payload = new CustomerPreferences();
        when(accountDataMapper.findAccountById(5L)).thenReturn(null);
        assertThrows(AccountNotFoundException.class, () -> service.upsertPreferences(5L, payload));

        when(accountDataMapper.findAccountById(5L)).thenReturn(new Account());
        CustomerPreferences saved = new CustomerPreferences();
        saved.setAccountId(5L);
        when(mapper.findByAccountId(5L)).thenReturn(saved);

        assertSame(saved, service.upsertPreferences(5L, payload));
        assertEquals(5L, payload.getAccountId());
        verify(mapper).upsertCustomerPreferences(payload);
    }

    @Test
    void resolveChannels_defaultsAndSelectsEventList() {
        when(mapper.findByAccountId(5L)).thenReturn(null);
        assertEquals(List.of("IN_APP", "EMAIL"), service.resolveChannels(5L, "PRICE_ALERTS"));

        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setNotificationsPriceAlerts(List.of("EMAIL"));
        prefs.setNotificationsTradeFills(List.of("IN_APP"));
        when(mapper.findByAccountId(5L)).thenReturn(prefs);

        assertEquals(List.of("EMAIL"), service.resolveChannels(5L, "PRICE_ALERTS"));
        assertEquals(List.of("IN_APP"), service.resolveChannels(5L, "TRADE_FILLS"));
        assertEquals(List.of("IN_APP"), service.resolveChannels(5L, null));
    }
}

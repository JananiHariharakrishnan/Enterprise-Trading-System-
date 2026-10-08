package com.tradingsystem.controller;

import com.tradingsystem.model.CustomerPreferences;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.CustomerPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerPreferencesControllerTest {

    @Mock
    private CustomerPreferencesService service;
    @Mock
    private CurrentUser currentUser;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerPreferencesController(service, currentUser)).build();
    }

    @Test
    void getPreferences_forbidsOtherAccountsAndReturnsOwn() throws Exception {
        when(currentUser.getAccountId()).thenReturn(9L);
        mockMvc.perform(get("/api/v1/accounts/5/preferences"))
                .andExpect(status().isForbidden());
        verify(service, never()).getPreferences(any());

        when(currentUser.getAccountId()).thenReturn(null);
        mockMvc.perform(get("/api/v1/accounts/5/preferences"))
                .andExpect(status().isForbidden());

        when(currentUser.getAccountId()).thenReturn(5L);
        CustomerPreferences prefs = new CustomerPreferences();
        prefs.setAccountId(5L);
        prefs.setDisplayTheme("dark");
        when(service.getPreferences(5L)).thenReturn(prefs);

        mockMvc.perform(get("/api/v1/accounts/5/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(5));
    }

    @Test
    void upsertPreferences_forbidsMismatchAndSavesMatch() throws Exception {
        when(currentUser.getAccountId()).thenReturn(9L);
        mockMvc.perform(put("/api/v1/accounts/5/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayTheme\":\"light\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).upsertPreferences(any(), any());

        when(currentUser.getAccountId()).thenReturn(5L);
        CustomerPreferences saved = new CustomerPreferences();
        saved.setAccountId(5L);
        when(service.upsertPreferences(eq(5L), any(CustomerPreferences.class))).thenReturn(saved);

        mockMvc.perform(put("/api/v1/accounts/5/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayTheme\":\"light\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(5));
    }

    @Test
    void resolve_checksInternalTokenAndReturnsChannels() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/internal/5/notifications/resolve")
                        .param("eventType", "PRICE_ALERTS"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/accounts/internal/5/notifications/resolve")
                        .param("eventType", "PRICE_ALERTS")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());

        String token = System.getenv().getOrDefault("INTERNAL_SERVICE_TOKEN", "dev-internal-token");
        when(service.resolveChannels(5L, "PRICE_ALERTS")).thenReturn(List.of("EMAIL", "IN_APP"));

        mockMvc.perform(get("/api/v1/accounts/internal/5/notifications/resolve")
                        .param("eventType", "PRICE_ALERTS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(5))
                .andExpect(jsonPath("$.eventType").value("PRICE_ALERTS"))
                .andExpect(jsonPath("$.channels[0]").value("EMAIL"))
                .andExpect(jsonPath("$.source").value("PREFERENCE"));

        mockMvc.perform(get("/api/v1/accounts/internal/5/notifications/resolve")
                        .param("eventType", "TRADE_FILLS")
                        .header("Authorization", "bearer   " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("TRADE_FILLS"));

        mockMvc.perform(get("/api/v1/accounts/internal/8/notifications/resolve")
                        .param("eventType", "TRADE_FILLS")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(8));
    }
}

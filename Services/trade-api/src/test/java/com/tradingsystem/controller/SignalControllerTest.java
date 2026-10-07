package com.tradingsystem.controller;

import com.tradingsystem.ai.signal.PortfolioContext;
import com.tradingsystem.ai.signal.SignalEngine;
import com.tradingsystem.ai.signal.TradeSignalStore;
import com.tradingsystem.dto.response.TradeSignalResponse;
import com.tradingsystem.mapper.OrderDataMapper;
import com.tradingsystem.model.Account;
import com.tradingsystem.model.AiSignal;
import com.tradingsystem.model.Position;
import com.tradingsystem.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SignalControllerTest {

    @Mock
    private TradeSignalStore signalStore;
    @Mock
    private SignalEngine signalEngine;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrderDataMapper orderDataMapper;

    private SignalController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new SignalController(signalStore, signalEngine, currentUser, orderDataMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllSignals_unauthenticatedOrNoOrders_returnsEmpty() throws Exception {
        doThrow(new IllegalStateException("anon")).when(currentUser).getAccountId();
        mockMvc.perform(get("/api/v1/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        doReturn(null).when(currentUser).getAccountId();
        mockMvc.perform(get("/api/v1/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        doReturn(7L).when(currentUser).getAccountId();
        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(null);
        mockMvc.perform(get("/api/v1/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(signalEngine, never()).generateSignalForSymbol(any(), any(), any(), eq(true));
    }

    @Test
    void getAllSignals_mixesStoredGeneratedAndNullSignals() throws Exception {
        when(currentUser.getAccountId()).thenReturn(7L);
        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(List.of("AAPL", "MSFT", "GONE"));
        when(signalStore.getBySymbol("AAPL")).thenReturn(Optional.of(signal("AAPL", "BUY")));
        when(signalStore.getBySymbol("MSFT")).thenReturn(Optional.empty());
        when(signalStore.getBySymbol("GONE")).thenReturn(Optional.empty());

        Account account = new Account();
        account.setCashBalance(new BigDecimal("5000"));
        account.setBuyingPower(new BigDecimal("8000"));
        when(orderDataMapper.findAccountById(7L)).thenReturn(account);
        Position position = new Position();
        position.setQuantity(12);
        position.setAverageCost(new BigDecimal("101.5"));
        when(orderDataMapper.findPosition(7L, "MSFT")).thenReturn(position);
        when(orderDataMapper.findPosition(7L, "GONE")).thenReturn(null);

        ArgumentCaptor<PortfolioContext> context = ArgumentCaptor.forClass(PortfolioContext.class);
        when(signalEngine.generateSignalForSymbol(eq("MSFT"), isNull(), context.capture(), eq(true)))
                .thenReturn(signal("MSFT", "HOLD"));
        when(signalEngine.generateSignalForSymbol(eq("GONE"), isNull(), any(), eq(true))).thenReturn(null);

        mockMvc.perform(get("/api/v1/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].action").value("BUY"))
                .andExpect(jsonPath("$[0].methodology").exists())
                .andExpect(jsonPath("$[0].disclaimer").exists())
                .andExpect(jsonPath("$[0].candleCacheDuration").exists())
                .andExpect(jsonPath("$[1].symbol").value("MSFT"))
                .andExpect(jsonPath("$[1].rsi").value(55.0));

        PortfolioContext built = context.getValue();
        assertEquals(7L, built.getAccountId());
        assertEquals(0, new BigDecimal("5000").compareTo(built.getCashBalance()));
        assertEquals(0, new BigDecimal("8000").compareTo(built.getBuyingPower()));
        assertEquals(12, built.getCurrentHoldingQuantity());
        assertEquals(0, new BigDecimal("101.5").compareTo(built.getCurrentAverageCost()));
    }

    @Test
    void getAllSignals_portfolioLookupFailure_stillGenerates() throws Exception {
        when(currentUser.getAccountId()).thenReturn(7L);
        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(List.of("IBM"));
        when(signalStore.getBySymbol("IBM")).thenReturn(Optional.empty());
        when(orderDataMapper.findAccountById(7L)).thenThrow(new RuntimeException("db down"));
        when(signalEngine.generateSignalForSymbol(eq("IBM"), isNull(), any(PortfolioContext.class), eq(true)))
                .thenReturn(signal("IBM", "SELL"));

        mockMvc.perform(get("/api/v1/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SELL"));
    }

    @Test
    void getAllSignals_missingAccountAndPosition_usesZeroHolding() throws Exception {
        when(currentUser.getAccountId()).thenReturn(7L);
        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(List.of("TSLA"));
        when(signalStore.getBySymbol("TSLA")).thenReturn(Optional.empty());
        when(orderDataMapper.findAccountById(7L)).thenReturn(null);
        when(orderDataMapper.findPosition(7L, "TSLA")).thenReturn(null);
        ArgumentCaptor<PortfolioContext> context = ArgumentCaptor.forClass(PortfolioContext.class);
        when(signalEngine.generateSignalForSymbol(eq("TSLA"), isNull(), context.capture(), eq(true)))
                .thenReturn(signal("TSLA", "HOLD"));

        mockMvc.perform(get("/api/v1/signals")).andExpect(status().isOk());

        assertNull(context.getValue().getCashBalance());
        assertEquals(0, context.getValue().getCurrentHoldingQuantity());
        assertEquals(0, BigDecimal.ZERO.compareTo(context.getValue().getCurrentAverageCost()));
    }

    @Test
    void getSignal_foundAndMissing() throws Exception {
        when(signalStore.getBySymbol("AAPL")).thenReturn(Optional.of(signal("AAPL", "BUY")));
        mockMvc.perform(get("/api/v1/signals/aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));

        when(signalStore.getBySymbol("NONE")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/signals/none"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateSignal_successNullAndAnonymous() throws Exception {
        doReturn(7L).when(currentUser).getAccountId();
        when(orderDataMapper.findAccountById(7L)).thenReturn(new Account());
        when(orderDataMapper.findPosition(7L, "aapl")).thenReturn(null);
        when(signalEngine.generateSignalForSymbol(eq("aapl"), eq(12.5), any(PortfolioContext.class), eq(true)))
                .thenReturn(signal("AAPL", "BUY"));

        mockMvc.perform(post("/api/v1/signals/generate/aapl").param("price", "12.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("BUY"));

        when(signalEngine.generateSignalForSymbol(eq("aapl"), isNull(), any(), eq(true))).thenReturn(null);
        mockMvc.perform(post("/api/v1/signals/generate/aapl"))
                .andExpect(status().isBadRequest());

        doReturn(null).when(currentUser).getAccountId();
        when(signalEngine.generateSignalForSymbol(eq("aapl"), isNull(), isNull(), eq(true)))
                .thenReturn(signal("AAPL", "HOLD"));
        mockMvc.perform(post("/api/v1/signals/generate/aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("HOLD"));
    }

    @Test
    void refreshAll_coversEmptyAndGeneratedSignals() throws Exception {
        doThrow(new RuntimeException("boom")).when(currentUser).getAccountId();
        mockMvc.perform(post("/api/v1/signals/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        doReturn(7L).when(currentUser).getAccountId();
        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(List.of());
        mockMvc.perform(post("/api/v1/signals/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(null);
        mockMvc.perform(post("/api/v1/signals/refresh"))
                .andExpect(jsonPath("$").isEmpty());

        when(orderDataMapper.findDistinctOrderedSymbolsByAccountId(7L)).thenReturn(List.of("AAPL", "MSFT"));
        when(orderDataMapper.findAccountById(7L)).thenReturn(null);
        when(orderDataMapper.findPosition(eq(7L), any())).thenReturn(null);
        when(signalEngine.generateSignalForSymbol(eq("AAPL"), isNull(), any(), eq(true))).thenReturn(signal("AAPL", "BUY"));
        when(signalEngine.generateSignalForSymbol(eq("MSFT"), isNull(), any(), eq(true))).thenReturn(null);

        mockMvc.perform(post("/api/v1/signals/refresh").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void nullCurrentUser_returnsEmptyOrGeneratesWithoutPortfolio() {
        SignalController anonymous = new SignalController(signalStore, signalEngine, null, orderDataMapper);
        assertTrue(anonymous.getAllSignals().getBody().isEmpty());
        assertTrue(anonymous.refreshAll().getBody().isEmpty());

        when(signalEngine.generateSignalForSymbol("IBM", null, null, true)).thenReturn(null);
        assertEquals(400, anonymous.generateSignal("IBM", null).getStatusCode().value());
    }

    private static AiSignal signal(String symbol, String action) {
        AiSignal signal = new AiSignal(symbol, action, 0.8, "because indicators", 55.0, 11.0, 10.0, 12.0);
        signal.setGeneratedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return signal;
    }
}

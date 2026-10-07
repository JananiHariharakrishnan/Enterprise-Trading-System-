package com.tradingsystem.controller;

import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.model.StrategyType;
import com.tradingsystem.automation.service.StrategyAutomationService;
import com.tradingsystem.security.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StrategyControllerGapsTest {

    @Mock
    private StrategyAutomationService automationService;

    @InjectMocks
    private StrategyController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void createStrategy_usesExplicitIdsPeriodsAndAuthenticatedAccount() throws Exception {
        AuthContextHolder.setAccountId(99L);
        String blankId = """
                {
                  "strategyId": "   ",
                  "symbol": "infy.ns",
                  "type": "BOLLINGER_BANDS",
                  "quantity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(99))
                .andExpect(jsonPath("$.symbol").value("INFY.NS"))
                .andExpect(jsonPath("$.strategyId").isNotEmpty());

        String explicit = """
                {
                  "strategyId": "my-strat",
                  "accountId": 42,
                  "symbol": "aapl",
                  "type": "MA_CROSSOVER",
                  "quantity": 2,
                  "fastPeriod": 8,
                  "slowPeriod": 21,
                  "bbPeriod": 15,
                  "bbStdDev": 2.5,
                  "maxSpend": 1000,
                  "maxPositionSize": 10,
                  "maxConsecutiveFailures": 4
                }
                """;

        mockMvc.perform(post("/api/v1/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(explicit))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.strategyId").value("my-strat"))
                .andExpect(jsonPath("$.accountId").value(42))
                .andExpect(jsonPath("$.symbol").value("AAPL"));

        ArgumentCaptor<StrategyInstance> captor = ArgumentCaptor.forClass(StrategyInstance.class);
        verify(automationService, org.mockito.Mockito.times(2)).registerStrategy(captor.capture());
        StrategyInstance created = captor.getAllValues().get(1);
        assertEquals("my-strat", created.getStrategyId());
        assertEquals(42L, created.getAccountId());
        assertEquals(8, created.getFastPeriod());
        assertEquals(21, created.getSlowPeriod());
        assertEquals(15, created.getBbPeriod());
        assertEquals(2.5, created.getBbStdDev());
        assertEquals(10, created.getMaxPositionSize());
        assertEquals(4, created.getMaxConsecutiveFailures());
        assertEquals(0, new BigDecimal("1000").compareTo(created.getMaxSpend()));
        assertTrue(captor.getAllValues().get(0).getStrategyId().startsWith("strat-"));
    }

    @Test
    void getEnableDisableAndDelete_coverFoundAndMissingStrategies() throws Exception {
        StrategyInstance strategy = new StrategyInstance(
                "strat-9", 1L, "MSFT", StrategyType.MA_CROSSOVER, 3,
                5, 20, 20, 2.0, BigDecimal.TEN, 100, 3
        );
        when(automationService.getStrategy("strat-9")).thenReturn(strategy);
        when(automationService.getStrategy("missing")).thenReturn(null);

        mockMvc.perform(get("/api/v1/strategies/strat-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyId").value("strat-9"))
                .andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(get("/api/v1/strategies/missing"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/strategies/strat-9/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("MSFT"));
        mockMvc.perform(post("/api/v1/strategies/missing/enable"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/strategies/missing/disable"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/strategies/strat-9"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/strategies/missing"))
                .andExpect(status().isNotFound());

        verify(automationService).enableStrategy("strat-9");
        verify(automationService).enableStrategy("missing");
        verify(automationService).disableStrategy("missing");
        verify(automationService).unregisterStrategy("strat-9");
    }
}

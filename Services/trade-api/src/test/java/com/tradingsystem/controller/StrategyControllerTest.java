package com.tradingsystem.controller;

import com.tradingsystem.automation.model.StrategyInstance;
import com.tradingsystem.automation.model.StrategyType;
import com.tradingsystem.automation.service.StrategyAutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StrategyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StrategyAutomationService automationService;

    @InjectMocks
    private StrategyController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/strategies creates a new strategy and returns 201")
    void testCreateStrategy() throws Exception {
        String json = """
            {
              "symbol": "AAPL",
              "type": "MA_CROSSOVER",
              "quantity": 10,
              "maxSpend": 50000,
              "maxPositionSize": 200
            }
            """;

        mockMvc.perform(post("/api/v1/strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.type").value("MA_CROSSOVER"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(automationService, times(1)).registerStrategy(any(StrategyInstance.class));
    }

    @Test
    @DisplayName("GET /api/v1/strategies returns list of strategies")
    void testGetAllStrategies() throws Exception {
        StrategyInstance strat = new StrategyInstance(
                "strat-123", 1L, "MSFT", StrategyType.BOLLINGER_BANDS, 25,
                5, 20, 20, 2.0, BigDecimal.valueOf(100000), 500, 3
        );

        when(automationService.getAllStrategies()).thenReturn(List.of(strat));

        mockMvc.perform(get("/api/v1/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyId").value("strat-123"))
                .andExpect(jsonPath("$[0].symbol").value("MSFT"));
    }

    @Test
    @DisplayName("POST /api/v1/strategies/{id}/disable disables strategy")
    void testDisableStrategy() throws Exception {
        StrategyInstance strat = new StrategyInstance(
                "strat-123", 1L, "MSFT", StrategyType.BOLLINGER_BANDS, 25,
                5, 20, 20, 2.0, BigDecimal.valueOf(100000), 500, 3
        );
        strat.disable();

        when(automationService.getStrategy("strat-123")).thenReturn(strat);

        mockMvc.perform(post("/api/v1/strategies/strat-123/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyId").value("strat-123"))
                .andExpect(jsonPath("$.enabled").value(false));

        verify(automationService, times(1)).disableStrategy("strat-123");
    }

    @Test
    @DisplayName("POST /api/v1/strategies/simulate-tick pushes quote to service")
    void testSimulateTick() throws Exception {
        String json = """
            {
              "symbol": "AAPL",
              "price": 185.50
            }
            """;

        mockMvc.perform(post("/api/v1/strategies/simulate-tick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TICK_PROCESSED"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.price").value(185.50));

        verify(automationService, times(1)).onQuote(any());
    }
}

